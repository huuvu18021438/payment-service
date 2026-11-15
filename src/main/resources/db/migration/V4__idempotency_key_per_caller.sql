-- Idempotency-Key chỉ có hiệu lực trong phạm vi người gọi: hai người dùng khác nhau có thể dùng cùng key
-- mà không nhìn thấy/đụng chạm giao dịch của nhau.
ALTER TABLE payment DROP CONSTRAINT uq_payment_idempotency_key;
ALTER TABLE payment ADD CONSTRAINT uq_payment_caller_idempotency_key UNIQUE (created_by, idempotency_key);
