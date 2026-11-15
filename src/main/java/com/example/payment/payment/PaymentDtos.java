package com.example.payment.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** customerId = Keycloak "sub" của khách hàng sở hữu giao dịch. */
    public record CreatePaymentRequest(
            @NotBlank @Size(max = 64) String customerId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @Size(max = 255) String description) {
    }

    public record PaymentResponse(
            UUID id,
            String customerId,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            String description,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {

        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getOwnerSub(), p.getAmount(), p.getCurrency(), p.getStatus(),
                    p.getDescription(), p.getCreatedAt(), p.getCreatedBy(), p.getUpdatedAt(), p.getUpdatedBy());
        }
    }
}
