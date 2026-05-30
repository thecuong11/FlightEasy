package com.flighteasy.scheduler;

import com.flighteasy.entity.EmailLog;
import com.flighteasy.repository.EmailLogRepository;
import com.flighteasy.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailRetryScheduler {

    private final EmailService emailService;
    private final EmailLogRepository emailLogRepository;

    @Scheduled(fixedRate = 60_000)
    public void retryFailedEmails() {
        List<EmailLog> pending = emailLogRepository.findPendingRetries(LocalDateTime.now());

        if (!pending.isEmpty()) {
            log.info("Retring {} failed emails", pending.size());
            pending.forEach(emailLog -> {
                try {
                    emailService.retrySend(emailLog);
                } catch (Exception e) {
                    log.error("Retry failed for email {}: {}", emailLog.getId(), e.getMessage());
                }
            });
        }
    }
}
