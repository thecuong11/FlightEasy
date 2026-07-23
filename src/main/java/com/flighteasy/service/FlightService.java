package com.flighteasy.service;

import com.flighteasy.dto.CreateFlightClassRequest;
import com.flighteasy.dto.CreateFlightRequest;
import com.flighteasy.dto.FlightResponse;
import com.flighteasy.dto.UpdateFlightStatusRequest;
import com.flighteasy.entity.*;
import com.flighteasy.enums.FlightStatus;
import com.flighteasy.event.FlightCancelledEvent;
import com.flighteasy.exception.custom.DuplicateException;
import com.flighteasy.exception.custom.InvalidFlightException;
import com.flighteasy.exception.custom.InvalidStatusTransittionException;
import com.flighteasy.exception.custom.NotFoundException;
import com.flighteasy.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FlightService {

    private final FlightRepository flightRepository;
    private final FlightClassRepository flightClassRepository;
    private final SeatRepository seatRepository;
    private final AirportRepository airportRepository;
    private final AirlineRepository airlineRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingService bookingService;

    public List<Airport> getAllAirport(){
        return airportRepository.findByIsActiveTrue();
    }

    public Airport getAirportByIata(String iata){
        return airportRepository.findByIataCode(iata.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Sân bay không tồn tại: " + iata));
    }

    @Transactional
    public Airport createAirport(Airport airport){
        if (airportRepository.existsByIataCode(airport.getIataCode())){
            throw new DuplicateException("Mã IATA đã tồn tại: " + airport.getIataCode());
        }
        return airportRepository.save(airport);
    }

    public FlightResponse getFlightById(Long id){
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Chuyến bay không tồn tại: " + id));
        return toFlightResponse(flight);
    }

    @Transactional
    public FlightResponse createFlight(CreateFlightRequest request){
        if (!request.arrivalTime().isAfter(request.departureTime())){
            throw new InvalidFlightException("Giờ đến phải sau giờ khởi hành");
        }

        Airport origin = getAirportByIata(request.originIata());
        Airport destination = getAirportByIata(request.destinationIata());
        if (origin.getId().equals(destination.getId())){
            throw new InvalidFlightException("Điểm đi và điểm đến không được trùng nhau");
        }

        LocalDate departDate = request.departureTime().toLocalDate();
        if (flightRepository.existsByFlightNumberAndDate(request.flightNumber(), departDate, 0L)){
            throw new DuplicateException("Mã chuyến bay đã tồn tại trong ngày " + departDate);
        }

        Airline airline = airlineRepository.findById(request.airlineId())
                .orElseThrow(() -> new NotFoundException("Hãng bay không tồn tại"));
        AircraftType aircraftType = request.aircraftTypeId() != null
                ? aircraftTypeRepository.findById(request.aircraftTypeId()).orElse(null)
                : null;

        int duration = (int) Duration.between(request.departureTime(), request.arrivalTime()).toMinutes();
        Flight flight = Flight.builder()
                .flightNumber(request.flightNumber())
                .airline(airline)
                .aircraftType(aircraftType)
                .origin(origin)
                .destination(destination)
                .departureTime(request.departureTime())
                .arrivalTime(request.arrivalTime())
                .durationMinutes(duration)
                .status(FlightStatus.SCHEDULED)
                .terminal(request.terminal())
                .gate(request.gate())
                .build();
        flight = flightRepository.save(flight);

        for (CreateFlightClassRequest classReq : request.classes()) {
            FlightClass fc = FlightClass.builder()
                    .flight(flight)
                    .classType(classReq.classType())
                    .basePrice(classReq.basePrice())
                    .totalSeats(classReq.totalSeats())
                    .availableSeats(classReq.totalSeats())
                    .baggageAllowanceKg(classReq.baggageAllowanceKg() != null ? classReq.baggageAllowanceKg() : 20)
                    .isRefundable(classReq.isRefundable())
                    .refundFeePercent(classReq.refundFeePercent() != null ? classReq.refundFeePercent() : 0)
                    .build();
            flightClassRepository.save(fc);
        }

        if (aircraftType != null){
            generateSeats(flight, aircraftType);
        }
        return toFlightResponse(flight);
    }

    @Transactional
    public void updateFlightStatus(Long flightId, UpdateFlightStatusRequest request){
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new NotFoundException("Chuyến bay không tồn tại: " + flightId));

        validateStatusTransition(flight.getStatus(), request.status());

        flight.setStatus(request.status());

        if (request.status() == FlightStatus.DELAYED && request.delayMinutes() != null){
            flight.setDelayMinutes(request.delayMinutes());
            flight.setDepartureTime(flight.getDepartureTime().plusMinutes(request.delayMinutes()));
        }
        flightRepository.save(flight);

        if (request.status() == FlightStatus.CANCELLED){
            bookingService.cancelBookingsForCancelledFlight(flight);
        }
    }

    private void generateSeats(Flight flight, AircraftType aircraft){
        List<Seat> seats = new ArrayList<>();
        int rowNum = 1;

        for (int i = 0; i < aircraft.getFirstClassSeats() / 2; i++){
            seats.add(buildSeat(flight, rowNum, "A", "FIRST_CLASS", "WINDOW", false));
            seats.add(buildSeat(flight, rowNum, "C", "FIRST_CLASS", "AISLE", false));
            rowNum++;
        }

        for (int i = 0; i < aircraft.getBusinessSeats() / 4; i++){
            seats.add(buildSeat(flight, rowNum, "A", "BUSINESS", "WINDOW", false));
            seats.add(buildSeat(flight, rowNum, "C", "BUSINESS", "AISLE", false));
            seats.add(buildSeat(flight, rowNum, "D", "BUSINESS", "AISLE", false));
            seats.add(buildSeat(flight, rowNum, "F", "BUSINESS", "WINDOW", false));
            rowNum++;
        }

        for (int i = 0; i < aircraft.getEconomySeats() / 6; i++){
            boolean isExtraLegroom = (rowNum == 14 || rowNum == 15);
            seats.add(buildSeat(flight, rowNum, "A", "ECONOMY", "WINDOW", isExtraLegroom));
            seats.add(buildSeat(flight, rowNum, "B", "ECONOMY", "MIDDLE", isExtraLegroom));
            seats.add(buildSeat(flight, rowNum, "C", "ECONOMY", "AISLE", isExtraLegroom));
            seats.add(buildSeat(flight, rowNum, "D", "ECONOMY", "AISLE", isExtraLegroom));
            seats.add(buildSeat(flight, rowNum, "E", "ECONOMY", "MIDDLE", isExtraLegroom));
            seats.add(buildSeat(flight, rowNum, "F", "ECONOMY", "WINDOW", isExtraLegroom));
            rowNum++;
        }

        seatRepository.saveAll(seats);
    }

    private Seat buildSeat(Flight flight, int row, String col, String classType, String position, boolean extraLegroom){
        return Seat.builder()
                .flight(flight)
                .seatNumber(row + col)
                .classType(classType)
                .position(position)
                .rowNumber(row)
                .isAvailable(true)
                .isExtraLegroom(extraLegroom)
                .extraFee(extraLegroom ? BigDecimal.valueOf(150000) : BigDecimal.ZERO)
                .build();
    }

    private void validateStatusTransition(FlightStatus current, FlightStatus next){
        Map<FlightStatus, Set<FlightStatus>> allowed = Map.of(
                FlightStatus.SCHEDULED, Set.of(FlightStatus.BOARDING, FlightStatus.DELAYED, FlightStatus.CANCELLED),
                FlightStatus.DELAYED, Set.of(FlightStatus.BOARDING, FlightStatus.CANCELLED),
                FlightStatus.BOARDING, Set.of(FlightStatus.DEPARTED),
                FlightStatus.DEPARTED, Set.of(FlightStatus.ARRIVED)
        );
        if (!allowed.getOrDefault(current, Set.of()).contains(next)){
            throw new InvalidStatusTransittionException("Không thể chuyển từ " + current + " sang " + next);
        }
    }

    private FlightResponse toFlightResponse(Flight f){
        return new FlightResponse(
                f.getId(), f.getFlightNumber(),
                new FlightResponse.AirlineInfo(f.getAirline().getIataCode(), f.getAirline().getName(), f.getAirline().getLogoUrl()),
                new FlightResponse.AirportInfo(f.getOrigin().getIataCode(), f.getOrigin().getName(), f.getOrigin().getCity()),
                new FlightResponse.AirportInfo(f.getDestination().getIataCode(), f.getDestination().getName(), f.getDestination().getCity()),
                f.getDepartureTime(), f.getArrivalTime(), f.getDurationMinutes(),
                f.getStatus().name(), f.getTerminal(), f.getGate(),
                f.getFlightClasses().stream().map(fc -> new FlightResponse.FlightClassInfo(
                        fc.getClassType(), fc.getBasePrice(),
                        fc.getAvailableSeats(), fc.getTotalSeats(),
                        fc.getBaggageAllowanceKg()
                )).toList()
        );
    }

    @Transactional
    public Airline createAirline(Airline airline) {
        if (airlineRepository.existsByIataCode(airline.getIataCode())) {
            throw new DuplicateException("Mã IATA hãng bay đã tồn tại: " + airline.getIataCode());
        }
        airline.setActive(true);
        return airlineRepository.save(airline);
    }

    public Page<FlightResponse> getAllFlights(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        if (status != null && !status.isBlank()) {
            FlightStatus flightStatus;
            try {
                flightStatus = FlightStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Trạng thái không hợp lệ: " + status);
            }
            return flightRepository.findByStatusOrderByDepartureTimeDesc(flightStatus, pageable)
                    .map(this::toFlightResponse);
        }

        return flightRepository.findAllByOrderByDepartureTimeDesc(pageable)
                .map(this::toFlightResponse);
    }

    public List<Airline> getAllAirlines() {
        return airlineRepository.findByIsActiveTrue();
    }

}
