package com.flighteasy.repository;

import com.flighteasy.dto.BookingReportRow;
import com.flighteasy.entity.Booking;
import com.flighteasy.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByPnrCode(String pnrCode);
    boolean existsByPnrCode(String pnrCode);

    @Query("""
        SELECT DISTINCT b FROM Booking b 
        LEFT JOIN FETCH b.segments s 
        LEFT JOIN FETCH s.passengers p
        LEFT JOIN FETCH p.seat
        LEFT JOIN FETCH s.flightClass fc
        LEFT JOIN FETCH fc.flight
        WHERE b.status = 'PENDING'
        AND b.expiresAt < :now
        """)
    List<Booking> findExpiredPending(@Param("now")LocalDateTime now);

    List<Booking> findByUserIdOrderByCreatedAtDesc (Long userId);

    @Query("""
        SELECT DISTINCT b FROM Booking b
        LEFT JOIN FETCH b.segments s
        LEFT JOIN FETCH s.passengers p
        LEFT JOIN FETCH p.seat
        LEFT JOIN FETCH s.flightClass fc
        LEFT JOIN FETCH fc.flight f
        LEFT JOIN FETCH f.origin
        LEFT JOIN FETCH f.destination
        WHERE b.id = :id
""")
    Optional<Booking> findByIdWithSegmentsAndPassengers(@Param("id") Long id);

    @Query("""
        SELECT DISTINCT b FROM Booking b
        JOIN b.segments s
        JOIN s.flightClass fc
        JOIN fc.flight f
        WHERE b.status = 'CONFIRMED'
        AND f.departureTime >= :start
        AND f.departureTime < :end
""")
    List<Booking> findConfirmedBookingsForCheckin(
            @Param("start")
            LocalDateTime start,
            @Param("end")
            LocalDateTime end
    );

    @Query(value = """
        SELECT
            COUNT (*) AS total_bookings,
            COUNT (CASE WHEN status IN ('CONFIRMED', 'COMPLETED') THEN 1 END) AS confirmed,
            COUNT (CASE WHEN status = 'PENDING' THEN 1 END) AS pending,
            COUNT (CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled,
            COALESCE (SUM(CASE WHEN status IN ('CONFIRMED', 'COMPLETED') THEN total_price END), 0) AS revenue,
            COALESCE (AVG(CASE WHEN status IN ('CONFIRMED', 'COMPLETED') THEN total_price END), 0) AS avg_price
        FROM bookings
        WHERE DATE(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') = :date
""", nativeQuery = true)
    Map<String, Object> getDailyStatus(@Param("date")LocalDate date);

    @Query(value = """
        SELECT DATE(confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS date,
               SUM(total_price) AS revenue,
               COUNT(*) AS bookings
           FROM bookings
           WHERE status IN ('CONFIRMED', 'COMPLETED')
           AND confirmed_at >= :fromDate AND confirmed_at < :toDate
           GROUP BY DATE(confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')
           ORDER BY date
""", nativeQuery = true)
    List<Map<String, Object>> getRevenueChart(
            @Param("fromDate")
            LocalDateTime fromDate,
            @Param("toDate")
            LocalDateTime toDate
    );

    @Query(value = """
        SELECT ao.iata_code AS origin, ad.iata_code AS destination,
               al.name AS airline, COUNT(b.id) AS total_bookings,
               SUM(b.total_price) AS total_revenue
        FROM bookings b
        JOIN booking_segments bs ON bs.booking_id = b.id
        JOIN flight_classes fc ON fc.id = bs.flight_class_id
        JOIN flights f ON f.id = fc.flight_id
        JOIN airports ao ON ao.id = f.origin_id
        JOIN airports ad ON ad.id = f.destination_id
        JOIN airlines al ON al.id = f.airline_id
        WHERE b.status IN ('CONFIRMED', 'COMPLETED')
        GROUP BY ao.iata_code, ad.iata_code, al.name
        ORDER BY total_bookings DESC
        LIMIT :limit
""", nativeQuery = true)
    List<Map<String, Object>> getTopRoutes(@Param("limit") int limit);

    @Query("""
        SELECT new com.flighteasy.dto.BookingReportRow(
            b.pnrCode,
            CAST(b.confirmedAt AS localdate ),
            CONCAT(ao.iataCode, ' → ', ad.iataCode),
            SIZE(bs.passengers),
            fc.classType,
            b.subtotal,
            b.serviceFee,
            b.totalPrice,
            CAST(b.status AS string),
            al.name 
        )
        FROM Booking b
        JOIN b.segments bs
        JOIN bs.flightClass fc
        JOIN fc.flight f
        JOIN f.origin ao
        JOIN f.destination ad
        JOIN f.airline al
        WHERE b.status IN ('CONFIRMED', 'COMPLETED')
        AND b.confirmedAt >= :fromDate AND b.confirmedAt <= :toDate
        ORDER BY b.confirmedAt DESC
""")
    List<BookingReportRow> findForReport(
            @Param("fromDate")
            LocalDateTime fromDate,
            @Param("toDate")
            LocalDateTime toDate
    );

    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);
}
