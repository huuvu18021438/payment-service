package com.example.payment.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Idempotency-Key có hiệu lực trong phạm vi người gọi (created_by). */
    Optional<Payment> findByCreatedByAndIdempotencyKey(String createdBy, String idempotencyKey);

    Page<Payment> findByOwnerSub(String ownerSub, Pageable pageable);

    /** Tổng tiền các giao dịch còn hiệu lực của khách, cùng loại tiền, từ thời điểm {@code since}. */
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.ownerSub = :owner and p.currency = :currency "
            + "and p.status = com.example.payment.payment.PaymentStatus.CREATED and p.createdAt >= :since")
    BigDecimal sumCreatedSince(@Param("owner") String owner, @Param("currency") String currency,
            @Param("since") Instant since);

    /**
     * Khoá advisory theo khách đến hết transaction để hai giao dịch song song không cùng lọt qua hạn mức ngày.
     * (Bọc trong subquery vì pg_advisory_xact_lock trả kiểu void, Hibernate không đọc được trực tiếp.)
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtext(:key))) t", nativeQuery = true)
    Integer lockCustomer(@Param("key") String key);
}
