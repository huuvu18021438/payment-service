package com.example.payment.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chỉ auditor và admin được xem nhật ký audit. */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final PaymentAuditLogRepository repository;

    public AuditLogController(PaymentAuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AUDITOR','PAYMENT_ADMIN')")
    public Page<PaymentAuditLog> list(Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAll(sorted);
    }
}
