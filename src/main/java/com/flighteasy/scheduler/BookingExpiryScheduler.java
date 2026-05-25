package com.flighteasy.scheduler;

import com.flighteasy.entity.Booking;
import com.flighteasy.repository.BookingRepository;
import com.flighteasy.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    @Scheduled(fixedDelay = 60_000)
    public void cancelExpiredBooking() {
        List<Booking> expired = bookingRepository.findExpiredPending(LocalDateTime.now());

        if (!expired.isEmpty()) {
            log.info("Found {} expired booking to cancel", expired.size());
            expired.forEach(booking -> {
                try {
                    bookingService.expireBooking(booking);
                } catch (Exception ex) {
                    log.error("Failed to expire booking {}: {}", booking.getPnrCode(), ex.getMessage());
                }
            });
        }
    }
}
