package com.example.payment.payment;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.payment.payment.PaymentDtos.CreatePaymentRequest;
import com.example.payment.payment.PaymentDtos.PaymentResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Phân quyền hai lớp: role (ai) + scope (token được cấp quyền gì).
 * Phân quyền theo dữ liệu (chỉ xem giao dịch của mình) nằm trong PaymentService.
 */
@RestController
@RequestMapping("/api/payments")
@Validated
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PAYMENT_OPERATOR','PAYMENT_ADMIN') and hasAuthority('SCOPE_payment:write')")
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        var result = service.create(idempotencyKey, request);
        PaymentResponse body = PaymentResponse.from(result.payment());
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/payments/" + body.id())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PAYMENT_VIEWER','PAYMENT_OPERATOR','PAYMENT_ADMIN') and hasAuthority('SCOPE_payment:read')")
    public PaymentResponse get(@PathVariable UUID id, Authentication auth) {
        return PaymentResponse.from(service.get(id, auth));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PAYMENT_VIEWER','PAYMENT_OPERATOR','PAYMENT_ADMIN') and hasAuthority('SCOPE_payment:read')")
    public Page<PaymentResponse> list(Pageable pageable, Authentication auth) {
        return service.list(pageable, auth).map(PaymentResponse::from);
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('PAYMENT_ADMIN') and hasAuthority('SCOPE_payment:refund')")
    public PaymentResponse refund(@PathVariable UUID id) {
        return PaymentResponse.from(service.refund(id));
    }
}
