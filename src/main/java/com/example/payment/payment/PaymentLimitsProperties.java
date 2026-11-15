package com.example.payment.payment;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hạn mức theo loại tiền (key = mã tiền tệ, ví dụ VND). Loại tiền không có trong danh sách bị từ chối.
 * perTransaction: tối đa mỗi giao dịch; daily: tổng tối đa mỗi khách trong một ngày.
 */
@ConfigurationProperties("app.payment")
public record PaymentLimitsProperties(Map<String, Limit> limits) {

    public PaymentLimitsProperties {
        limits = limits == null ? Map.of() : limits;
    }

    public record Limit(BigDecimal perTransaction, BigDecimal daily) {
    }
}
