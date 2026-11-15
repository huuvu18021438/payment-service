package com.example.payment.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.payment.audit.AuditService;
import com.example.payment.audit.CurrentUser;
import com.example.payment.payment.PaymentDtos.CreatePaymentRequest;

@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final AuditService audit;
    private final PaymentLimitsProperties limits;

    public PaymentService(PaymentRepository repository, AuditService audit, PaymentLimitsProperties limits) {
        this.repository = repository;
        this.audit = audit;
        this.limits = limits;
    }

    public record CreateResult(Payment payment, boolean created) {
    }

    /**
     * Idempotent theo (người gọi, Idempotency-Key): gửi lại cùng key + cùng nội dung -> trả giao dịch cũ, không tạo thêm.
     * Giao dịch mới phải qua kiểm tra hạn mức (422 nếu vượt).
     */
    @Transactional
    public CreateResult create(String idempotencyKey, CreatePaymentRequest req) {
        String caller = CurrentUser.name();
        var existing = repository.findByCreatedByAndIdempotencyKey(caller, idempotencyKey);
        if (existing.isPresent()) {
            return new CreateResult(sameRequest(existing.get(), req), false);
        }
        checkLimits(req);
        try {
            Payment saved = repository.saveAndFlush(new Payment(
                    req.customerId(), req.amount(), req.currency(), req.description(), idempotencyKey));
            audit.record(saved.getId(), "PAYMENT_CREATED", saved.getAmount() + " " + saved.getCurrency());
            return new CreateResult(saved, true);
        } catch (DataIntegrityViolationException race) {
            // Hai request cùng key chạy song song: request thua trả về bản ghi của request thắng
            Payment winner = repository.findByCreatedByAndIdempotencyKey(caller, idempotencyKey).orElseThrow(() -> race);
            return new CreateResult(sameRequest(winner, req), false);
        }
    }

    @Transactional // không readOnly: ghi audit PAYMENT_VIEWED
    public Payment get(UUID id, Authentication auth) {
        Payment p = repository.findById(id).orElseThrow(PaymentService::notFound);
        // Người không có quyền xem tất cả chỉ thấy giao dịch của mình; trả 404 để không lộ sự tồn tại
        if (!canSeeAll(auth) && !p.getOwnerSub().equals(subject(auth))) {
            throw notFound();
        }
        audit.record(id, "PAYMENT_VIEWED", null);
        return p;
    }

    @Transactional(readOnly = true)
    public Page<Payment> list(Pageable pageable, Authentication auth) {
        return canSeeAll(auth)
                ? repository.findAll(pageable)
                : repository.findByOwnerSub(subject(auth), pageable);
    }

    @Transactional
    public Payment refund(UUID id) {
        Payment p = repository.findById(id).orElseThrow(PaymentService::notFound);
        try {
            p.refund();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        Payment saved = repository.saveAndFlush(p);
        audit.record(id, "PAYMENT_REFUNDED", saved.getAmount() + " " + saved.getCurrency());
        return saved;
    }

    private void checkLimits(CreatePaymentRequest req) {
        var limit = limits.limits().get(req.currency());
        if (limit == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Currency not supported: " + req.currency());
        }
        if (req.amount().compareTo(limit.perTransaction()) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Amount exceeds per-transaction limit");
        }
        repository.lockCustomer(req.customerId());
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        BigDecimal usedToday = repository.sumCreatedSince(req.customerId(), req.currency(), startOfDay);
        if (usedToday.add(req.amount()).compareTo(limit.daily()) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Daily limit exceeded");
        }
    }

    private Payment sameRequest(Payment p, CreatePaymentRequest req) {
        boolean same = p.getOwnerSub().equals(req.customerId())
                && p.getAmount().compareTo(req.amount()) == 0
                && p.getCurrency().equals(req.currency())
                && java.util.Objects.equals(p.getDescription(), req.description());
        if (!same) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key already used with a different request");
        }
        return p;
    }

    private static boolean canSeeAll(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_PAYMENT_OPERATOR") || a.getAuthority().equals("ROLE_PAYMENT_ADMIN"));
    }

    private static String subject(Authentication auth) {
        return ((Jwt) auth.getPrincipal()).getSubject();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
    }
}
