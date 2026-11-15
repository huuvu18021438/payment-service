package com.example.payment.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.payment.audit.CurrentUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "owner_sub", nullable = false, updatable = false)
    private String ownerSub;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String description;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected Payment() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        String user = CurrentUser.name();
        this.createdAt = now;
        this.createdBy = user;
        this.updatedAt = now;
        this.updatedBy = user;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        this.updatedBy = CurrentUser.name();
    }

    public Payment(String ownerSub, BigDecimal amount, String currency, String description, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.ownerSub = ownerSub;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.CREATED;
    }

    public void refund() {
        if (status != PaymentStatus.CREATED) {
            throw new IllegalStateException("Payment cannot be refunded in status " + status);
        }
        this.status = PaymentStatus.REFUNDED;
    }

    public UUID getId() { return id; }
    public String getOwnerSub() { return ownerSub; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
