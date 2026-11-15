package com.example.payment.audit;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Ghi audit: ai (sub/username), làm gì, trên giao dịch nào, từ IP nào, correlation-id nào.
 * Không ghi token hay dữ liệu nhạy cảm vào detail.
 */
@Service
public class AuditService {

    private final PaymentAuditLogRepository repository;

    public AuditService(PaymentAuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(UUID paymentId, String action, String detail) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String sub = "anonymous";
        String username = null;
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            sub = jwt.getSubject();
            username = jwt.getClaimAsString("preferred_username");
        }
        String ip = null;
        String correlationId = null;
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            // Sau reverse proxy cần cấu hình forward-headers-strategy để getRemoteAddr đúng
            ip = sra.getRequest().getRemoteAddr();
            correlationId = sra.getRequest().getHeader("X-Correlation-Id");
        }
        repository.save(new PaymentAuditLog(paymentId, action, sub, username, ip, truncate(correlationId, 100), detail));
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
