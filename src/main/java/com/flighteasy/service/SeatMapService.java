package com.flighteasy.service;

import com.flighteasy.dto.SeatInfo;
import com.flighteasy.dto.SeatMapResponse;
import com.flighteasy.dto.SeatRow;
import com.flighteasy.entity.Seat;
import com.flighteasy.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatMapService {

    private final SeatRepository seatRepository;

    public SeatMapResponse getSeatMap(Long flightsId) {
        List<Seat> allSeats = seatRepository.findByFlightId(flightsId);

        Map<String, Map<Integer, List<Seat>>> grouped = allSeats.stream()
                .collect(Collectors.groupingBy(
                        Seat::getClassType,
                        Collectors.groupingBy(Seat::getRowNumber)
                ));
        return SeatMapResponse.builder()
                .firstClass(buildSection(grouped.get("FIRST_CLASS")))
                .business(buildSection(grouped.get("BUSINESS")))
                .economy(buildSection(grouped.get("ECONOMY")))
                .build();
    }

    private List<SeatRow> buildSection(Map<Integer, List<Seat>> rowMap) {
        if (rowMap == null) return List.of();
        return rowMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> SeatRow.builder()
                        .rowNumber(e.getKey())
                        .seats(e.getValue().stream().map(s -> SeatInfo.builder()
                                .seatNumber(s.getSeatNumber())
                                .position(s.getPosition())
                                .isAvailable(s.getIsAvailable())
                                .extraFee(s.getExtraFee())
                                .isExtraLegroom(s.getIsExtraLegroom())
                                .build())
                                .toList())
                        .build())
                .toList();

    }
}
