package com.flighteasy.scheduler;

import com.flighteasy.entity.Booking;
import com.flighteasy.repository.BookingRepository;
import com.flighteasy.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class CheckinReminderScheduler {

    private final BookingRepository bookingRepository;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Ho_Chi_Minh")
    public void sendCheckinReminders() {
        LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime tomorrowEnd = tomorrowStart.plusDays(1);

        List<Booking> bookings = bookingRepository.findConfirmedBookingsForCheckin(tomorrowStart, tomorrowEnd);

        log.info("Sending check-in reminders for {} bookings", bookings.size());
        bookings.forEach(emailService::sendCheckinReminder);
    }
}
