CREATE TABLE payment (
    id              UUID            PRIMARY KEY,
    owner_sub       VARCHAR(64)     NOT NULL,           -- Keycloak "sub" của khách hàng sở hữu giao dịch
    amount          NUMERIC(19, 2)  NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3)      NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    description     VARCHAR(255),
    idempotency_key VARCHAR(100)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    created_by      VARCHAR(100)    NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    updated_by      VARCHAR(100)    NOT NULL,
    CONSTRAINT uq_payment_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_payment_owner_sub ON payment (owner_sub);

-- Nhật ký audit: chỉ INSERT, không UPDATE/DELETE từ ứng dụng
CREATE TABLE payment_audit_log (
    id              BIGSERIAL       PRIMARY KEY,
    payment_id      UUID,
    action          VARCHAR(50)     NOT NULL,
    actor_sub       VARCHAR(64)     NOT NULL,
    actor_username  VARCHAR(100),
    ip_address      VARCHAR(64),
    correlation_id  VARCHAR(100),
    detail          VARCHAR(500),
    created_at      TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_audit_payment_id ON payment_audit_log (payment_id);
CREATE INDEX idx_audit_created_at ON payment_audit_log (created_at);
