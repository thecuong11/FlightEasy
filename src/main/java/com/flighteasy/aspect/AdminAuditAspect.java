package com.flighteasy.aspect;

import com.flighteasy.entity.AdminAuditLog;
import com.flighteasy.entity.User;
import com.flighteasy.repository.AdminAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAuditAspect {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final HttpServletRequest httpRequest;

    @AfterReturning(
            pointcut = "execution(* com.flighteasy.controller.AdminController.*(..))",
            returning = "result"
    )
    public void logAdminAction(JoinPoint joinPoint, Object result) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof User admin)) return;

            String methodName = joinPoint.getSignature().getName();

            AdminAuditLog auditLog = AdminAuditLog.builder()
                    .admin(admin)
                    .action(methodName)
                    .ipAddress(getClientIp())
                    .createdAt(LocalDateTime.now())
                    .build();

            adminAuditLogRepository.save(auditLog);
            log.info("Admin action logged: {} by {}", methodName, admin.getEmail());

        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    private String getClientIp() {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        return ip != null ? ip.split(",")[0].trim() : httpRequest.getRemoteAddr();
    }
}
