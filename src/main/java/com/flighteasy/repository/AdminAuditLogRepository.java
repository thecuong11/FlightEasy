package com.flighteasy.repository;

import com.flighteasy.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    List<AdminAuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId);
}
