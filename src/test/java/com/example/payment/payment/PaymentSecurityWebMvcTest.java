package com.example.payment.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.example.payment.audit.AuditLogController;
import com.example.payment.audit.PaymentAuditLogRepository;
import com.example.payment.security.SecurityConfig;

/** Kiểm tra 401 / 403 / 2xx theo role + scope, không cần Keycloak hay database. */
@WebMvcTest({PaymentController.class, AuditLogController.class})
@Import(SecurityConfig.class)
class PaymentSecurityWebMvcTest {

    private static final String BODY = """
            {"customerId":"cust-1","amount":10.50,"currency":"VND","description":"test"}""";

    @Autowired MockMvc mvc;
    @MockitoBean PaymentService service;
    @MockitoBean PaymentAuditLogRepository auditRepository;

    private static RequestPostProcessor token(String... authorities) {
        var list = java.util.Arrays.stream(authorities)
                .<org.springframework.security.core.GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
        return jwt().authorities(list);
    }

    private static Payment payment() {
        return new Payment("cust-1", new BigDecimal("10.50"), "VND", "test", "key-1");
    }

    @Test
    void noToken_returns401Json() throws Exception {
        mvc.perform(get("/api/payments"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payments").header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        // Không có actuator trong slice -> 404 nghĩa là đã qua lớp bảo mật (không phải 401)
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }

    @Test
    void viewer_cannotCreate_returns403() throws Exception {
        mvc.perform(post("/api/payments").with(token("ROLE_PAYMENT_VIEWER", "SCOPE_payment:read", "SCOPE_payment:write"))
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorWithoutWriteScope_returns403() throws Exception {
        mvc.perform(post("/api/payments").with(token("ROLE_PAYMENT_OPERATOR", "SCOPE_payment:read"))
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorWithWriteScope_creates201() throws Exception {
        when(service.create(any(), any())).thenReturn(new PaymentService.CreateResult(payment(), true));

        mvc.perform(post("/api/payments").with(token("ROLE_PAYMENT_OPERATOR", "SCOPE_payment:write"))
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void create_missingIdempotencyKey_returns400() throws Exception {
        mvc.perform(post("/api/payments").with(token("ROLE_PAYMENT_OPERATOR", "SCOPE_payment:write"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invalidBody_returns400() throws Exception {
        mvc.perform(post("/api/payments").with(token("ROLE_PAYMENT_OPERATOR", "SCOPE_payment:write"))
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"c\",\"amount\":-5,\"currency\":\"vnd\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void viewerWithReadScope_canList() throws Exception {
        when(service.list(any(), any())).thenReturn(Page.empty());

        mvc.perform(get("/api/payments").with(token("ROLE_PAYMENT_VIEWER", "SCOPE_payment:read")))
                .andExpect(status().isOk());
    }

    @Test
    void viewerWithoutReadScope_returns403() throws Exception {
        mvc.perform(get("/api/payments").with(token("ROLE_PAYMENT_VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void refund_requiresAdminRoleAndRefundScope() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.refund(id)).thenReturn(payment());

        mvc.perform(post("/api/payments/{id}/refund", id).with(token("ROLE_PAYMENT_OPERATOR", "SCOPE_payment:refund")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/{id}/refund", id).with(token("ROLE_PAYMENT_ADMIN", "SCOPE_payment:write")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/{id}/refund", id).with(token("ROLE_PAYMENT_ADMIN", "SCOPE_payment:refund")))
                .andExpect(status().isOk());
    }

    @Test
    void auditLogs_onlyAuditorOrAdmin() throws Exception {
        when(auditRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(Page.empty());

        mvc.perform(get("/api/audit-logs").with(token("ROLE_PAYMENT_OPERATOR", "SCOPE_payment:read")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit-logs").with(token("ROLE_AUDITOR")))
                .andExpect(status().isOk());
    }
}
