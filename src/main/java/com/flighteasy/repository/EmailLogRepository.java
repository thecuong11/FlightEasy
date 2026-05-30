package com.flighteasy.repository;

import com.flighteasy.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    @Query("SELECT e FROM EmailLog e WHERE e.status = 'PENDING' AND e.nextRetryAt <= :now")
    List<EmailLog> findPendingRetries(@Param("now") LocalDateTime now);

    boolean existsByReferenceIdAndTemplateName(String referenceId, String templateName);

    int countByReferenceIdAndTemplateName(String referenceId, String templateName);
}
