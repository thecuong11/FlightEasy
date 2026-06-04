package com.flighteasy.service;

import com.flighteasy.dto.DashboardKPIResponse;
import com.flighteasy.dto.RevenueChartData;
import com.flighteasy.dto.TopRouteResponse;
import com.flighteasy.enums.FlightStatus;
import com.flighteasy.repository.BookingRepository;
import com.flighteasy.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.BrokenWriter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardService {

    private final BookingRepository bookingRepository;
    private final FlightRepository flightRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KPI_CACHE_KEY = "admin:dashboard:kpis";
    private static final Duration KPI_TTL = Duration.ofMinutes(5);

    @SuppressWarnings("unchecked")
    public DashboardKPIResponse getDashboardKPIs() {
        Object cached = redisTemplate.opsForValue().get(KPI_CACHE_KEY);
        if (cached != null) {
            return (DashboardKPIResponse) cached;
        }

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        Map<String, Object> todayStarts = bookingRepository.getDailyStatus(today);
        Map<String, Object> yesterdayStarts = bookingRepository.getDailyStatus(yesterday);

        BigDecimal todayRevenue = toBigDecimal(todayStarts.get("revenue"));
        BigDecimal yesterdayRevenue = toBigDecimal(yesterdayStarts.get("revenue"));

        double growthPercent = yesterdayRevenue.compareTo(BigDecimal.ZERO) == 0
                ? 0
                : todayRevenue.subtract(yesterdayRevenue)
                  .divide(yesterdayRevenue, 4, RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(100))
                  .doubleValue();

        long todayBookings = toLong(todayStarts.get("total_bookings"));
        long confirmedBookings = toLong(todayStarts.get("confirmed"));
        double conversionRate = todayBookings == 0 ? 0
                : (double) confirmedBookings / todayBookings * 100;

        long totalFlight = flightRepository.countByDepartureDate(today);
        long delayedFlight = flightRepository.countByStatusAndDepartureDate(FlightStatus.DELAYED, today);
        long cancelledFlight = flightRepository.countByStatusAndDepartureDate(FlightStatus.CANCELLED, today);

        DashboardKPIResponse response = DashboardKPIResponse.builder()
                .date(today)
                .todayRevenue(todayRevenue)
                .yesterdayRevenue(yesterdayRevenue)
                .revenueGrowthPercent(growthPercent)
                .todayBookings(todayBookings)
                .confirmedBookings(confirmedBookings)
                .pendingBookings(toLong(todayStarts.get("pending")))
                .cancelledBookings(toLong(todayStarts.get("cancelled")))
                .conversionRate(conversionRate)
                .totalFlights(totalFlight)
                .delayedFlight(delayedFlight)
                .cancelledFlights(cancelledFlight)
                .avgTicketPrice(toBigDecimal(todayStarts.get("avg_price")))
                .updatedAt(LocalDateTime.now())
                .build();
        redisTemplate.opsForValue().set(KPI_CACHE_KEY, response, KPI_TTL);
        return response;
    }

    public RevenueChartData getRevenueChart(String period) {
        LocalDate from = switch (period) {
            case "WEEKLY"  -> LocalDate.now().minusDays(7);
            case "MONTHLY" -> LocalDate.now().minusDays(30);
            case "YEARLY"  -> LocalDate.now().minusDays(365);
            default        -> LocalDate.now().minusDays(30);
        };

        List<Map<String, Object>> raw = bookingRepository.getRevenueChart(
                from.atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay()
        );

        List<RevenueChartData.RevenuePoint> points = raw.stream()
                .map(row -> RevenueChartData.RevenuePoint.builder()
                        .date(((java.sql.Date)row.get("date")).toLocalDate())
                        .revenue(toBigDecimal(row.get("revenue")))
                        .bookings(toLong(row.get("bookings")))
                        .build())
                .toList();
        return RevenueChartData.builder().points(points).build();
    }

    public List<TopRouteResponse> getTopRoutes(int limit) {
        return bookingRepository.getTopRoutes(Math.min(limit, 20)).stream()
                .map(row -> new TopRouteResponse(
                        (String) row.get("origin"),
                        (String) row.get("destination"),
                        (String) row.get("airline"),
                        toLong(row.get("total_bookings")),
                        toBigDecimal(row.get("total_revenue"))
                ))
                .toList();
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(val.toString());
    }
}
