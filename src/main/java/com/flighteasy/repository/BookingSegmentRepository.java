package com.flighteasy.repository;

import com.flighteasy.entity.BookingSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingSegmentRepository extends JpaRepository<BookingSegment, Long> {
    List<BookingSegment> findByBookingId(Long bookingId);
}
