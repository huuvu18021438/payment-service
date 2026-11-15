package com.example.payment.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_audit_log")
public class PaymentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(name = "actor_sub", nullable = false, updatable = false)
    private String actorSub;

    @Column(name = "actor_username", updatable = false)
    private String actorUsername;

    @Column(name = "ip_address", updatable = false)
    private String ipAddress;

    @Column(name = "correlation_id", updatable = false)
    private String correlationId;

    @Column(updatable = false)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentAuditLog() {
    }

    public PaymentAuditLog(UUID paymentId, String action, String actorSub, String actorUsername,
                           String ipAddress, String correlationId, String detail) {
        this.paymentId = paymentId;
        this.action = action;
        this.actorSub = actorSub;
        this.actorUsername = actorUsername;
        this.ipAddress = ipAddress;
        this.correlationId = correlationId;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public String getAction() { return action; }
    public String getActorSub() { return actorSub; }
    public String getActorUsername() { return actorUsername; }
    public String getIpAddress() { return ipAddress; }
    public String getCorrelationId() { return correlationId; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
