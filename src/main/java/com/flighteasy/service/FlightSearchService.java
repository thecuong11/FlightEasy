package com.flighteasy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flighteasy.dto.FlightSearchRequest;
import com.flighteasy.dto.FlightSearchResponse;
import com.flighteasy.dto.FlightSearchResult;
import com.flighteasy.dto.RoundTripSearchResponse;
import com.flighteasy.enums.SortBy;
import com.flighteasy.enums.TimeRange;
import com.flighteasy.exception.custom.InvalidSearchException;
import com.flighteasy.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FlightSearchService {

    private final FlightRepository flightRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Executor searchExecutor;

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    public FlightSearchService(FlightRepository flightRepository, StringRedisTemplate redisTemplate, ObjectMapper objectMapper, @Qualifier("searchExecutor") Executor searchExecutor) {
        this.flightRepository = flightRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.searchExecutor = searchExecutor;
    }

    public FlightSearchResponse search(FlightSearchRequest request){
        if (!request.getDepartDate().isAfter(LocalDate.now())){
            throw new InvalidSearchException("Ngày tìm kiếm phải từ ngày mai trở đi");
        }

        if (request.getAdults() + request.getChildren() + request.getInfants() > 9){
            throw new InvalidSearchException("Tổng hành khác không được vượt quá 9");
        }

        String cacheKey = buildCacheKey(request);

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null){
            try {
                log.info("Cache HIT: {}",cacheKey);
                return objectMapper.readValue(cached, FlightSearchResponse.class);
            } catch (Exception e) {
                log.warn("Cache deserialize failed, query DB: {}", e.getMessage());
            }
        }

        List<FlightSearchResult> results = flightRepository.searchFlights(
                request.getFrom().toUpperCase(),
                request.getTo().toUpperCase(),
                request.getDepartDate(),
                request.getClassType().name(),
                request.getTotalPassengers()
        );

        results = applyFilter(results, request);

        results = applySorting(results, request.getSortBy());

        results.forEach(r -> calculateTotalPrice(r, request));

        tagFlights(results);

        FlightSearchResponse response = buildResponse(results, request);

        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(response),
                    CACHE_TTL
            );
        } catch (Exception e) {
            log.warn("Cache save failed: {}", e.getMessage());
        }

        return response;
    }

    public RoundTripSearchResponse searchRoundTrip(FlightSearchRequest request){
        if (request.getReturnDate() == null) {
            throw new InvalidSearchException("Vui lòng cung cấp ngày về cho chuyến khứ hồi");
        }

        FlightSearchRequest returnRequest = new FlightSearchRequest();
        returnRequest.setFrom(request.getTo());
        returnRequest.setTo(request.getFrom());
        returnRequest.setDepartDate(request.getReturnDate());
        returnRequest.setAdults(request.getAdults());
        returnRequest.setChildren(request.getChildren());
        returnRequest.setInfants(request.getInfants());
        returnRequest.setClassType(request.getClassType());
        returnRequest.setSortBy(request.getSortBy());

        CompletableFuture<FlightSearchResponse> outboundFuture = CompletableFuture.supplyAsync(() -> search(request), searchExecutor);
        CompletableFuture<FlightSearchResponse> returnFuture = CompletableFuture.supplyAsync(() -> search(returnRequest));

        CompletableFuture.allOf(outboundFuture, returnFuture).join();

        FlightSearchResponse outbound = outboundFuture.join();
        FlightSearchResponse returnTrip = returnFuture.join();

        RoundTripSearchResponse.FlightPair cheapest = findCheapestPair(outbound.flights(), returnTrip.flights());

        return new RoundTripSearchResponse(outbound, returnTrip, cheapest);
    }

    private List<FlightSearchResult> applyFilter(List<FlightSearchResult> flights, FlightSearchRequest req){
        return flights.stream()
                .filter(f -> req.getMinPrice() == null || f.getPricePerPerson().compareTo(req.getMinPrice()) >= 0)
                .filter(f -> req.getMaxPrice() == null || f.getPricePerPerson().compareTo(req.getMaxPrice()) <= 0)
                .filter(f -> req.getAirlines() == null || req.getAirlines().contains(f.getAirlineCode()))
                .filter(f -> req.getMaxDuration() == null || f.getDurationMinutes() <= req.getMaxDuration())
                .filter(f -> matchTimeRange(f.getDepartureTime(), req.getDepartTimeRange()))
                .collect(Collectors.toList());
    }

    private List<FlightSearchResult> applySorting(List<FlightSearchResult> flights, SortBy sortBy){
        Comparator<FlightSearchResult> comparator = switch (sortBy){
            case PRICE_ASC -> Comparator.comparing(FlightSearchResult::getPricePerPerson);
            case PRICE_DESC -> Comparator.comparing(FlightSearchResult::getPricePerPerson).reversed();
            case DURATION_ASC -> Comparator.comparing(FlightSearchResult::getDurationMinutes);
            case DEPARTURE_ASC -> Comparator.comparing(FlightSearchResult::getDepartureTime);
            case DEPARTURE_DESC -> Comparator.comparing(FlightSearchResult::getDepartureTime).reversed();
            case ARRIVAL_ASC -> Comparator.comparing(FlightSearchResult::getArrivalTime);
        };
        return flights.stream().sorted(comparator).collect(Collectors.toList());
    }

    private void calculateTotalPrice(FlightSearchResult flight, FlightSearchRequest req){
        BigDecimal adultPrice = flight.getPricePerPerson();
        BigDecimal childPrice = adultPrice.multiply(BigDecimal.valueOf(0.75));
        BigDecimal infantPrice = adultPrice.multiply(BigDecimal.valueOf(0.10));

        BigDecimal total = adultPrice.multiply(BigDecimal.valueOf(req.getAdults()))
                .add(childPrice.multiply(BigDecimal.valueOf(req.getChildren())))
                .add(infantPrice.multiply(BigDecimal.valueOf(req.getInfants())));

        flight.setTotalPrice(total);
    }

    private void tagFlights(List<FlightSearchResult> flights){
        if (flights.isEmpty()) return;

        BigDecimal minPrice = flights.stream()
                .map(FlightSearchResult::getPricePerPerson)
                .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        int minDuration = flights.stream()
                .mapToInt(FlightSearchResult::getDurationMinutes).min().orElse(0);

        flights.forEach(f -> {
            if (f.getPricePerPerson().compareTo(minPrice) == 0) f.addTag("CHEAPEST");
            if (f.getDurationMinutes() == minDuration) f.addTag("FASTEST");
        });
    }

    private boolean matchTimeRange(LocalDateTime departure, TimeRange range){
        if (range == null) return true;
        int hour = departure.getHour();
        return switch (range){
            case EARLY_MORNING -> hour >= 0 && hour < 6;
            case MORNING        -> hour >= 6 && hour < 12;
            case AFTERNOON      -> hour >= 12 && hour < 18;
            case EVENING        -> hour >= 18;
        };
    }

    private FlightSearchResponse buildResponse(List<FlightSearchResult> results, FlightSearchRequest req){
        int start = req.getPage() * req.getSize();
        int end = Math.min(start + req.getSize(), results.size());
        List<FlightSearchResult> paged = start < results.size() ? results.subList(start, end) : List.of();

        BigDecimal minPrice = results.stream().map(FlightSearchResult::getPricePerPerson)
                .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal maxPrice = results.stream().map(FlightSearchResult::getPricePerPerson)
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        List<String> airlines = results.stream()
                .map(FlightSearchResult::getAirlineCode).distinct().toList();
        int minDur = results.stream().mapToInt(FlightSearchResult::getDurationMinutes).min().orElse(0);
        int maxDur = results.stream().mapToInt(FlightSearchResult::getDurationMinutes).max().orElse(0);

        return new FlightSearchResponse(
                new FlightSearchResponse.SearchMeta(
                        req.getFrom(), req.getTo(), req.getDepartDate(),
                        req.getAdults(), req.getChildren(), req.getInfants(),
                        req.getClassType().name()
                ),
                paged,
                new FlightSearchResponse.PriceRange(minPrice, maxPrice),
                new FlightSearchResponse.AvailableFilters(airlines, new FlightSearchResponse.DurationRange(minDur, maxDur))
        );
    }

    private RoundTripSearchResponse.FlightPair findCheapestPair(List<FlightSearchResult> outbound, List<FlightSearchResult> returnFlights) {
        if (outbound.isEmpty() || returnFlights.isEmpty()) return null;

        FlightSearchResult cheapestOut = outbound.get(0);
        FlightSearchResult cheapestRet = returnFlights.get(0);
        BigDecimal total = cheapestOut.getPricePerPerson().add(cheapestRet.getPricePerPerson());

        return new RoundTripSearchResponse.FlightPair(cheapestOut, cheapestRet, total);
    }

    private String buildCacheKey(FlightSearchRequest r) {
        String filterHash = Integer.toHexString(Objects.hash(
                r.getMinPrice(), r.getMaxPrice(), r.getAirlines(),
                r.getMaxDuration(), r.getDepartTimeRange()
        ));
        return String.format("flight-search:%s:%s:%s:%s:%d:%s:%s:p%d:s%d",
                r.getFrom(), r.getTo(), r.getDepartDate(),
                r.getClassType(), r.getTotalPassengers(),
                r.getSortBy(), filterHash, r.getPage(), r.getSize());
    }
}
