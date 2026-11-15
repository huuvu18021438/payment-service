-- Cưỡng chế audit log chỉ-ghi-thêm ngay tại DB: UPDATE/DELETE/TRUNCATE đều bị từ chối.
-- Lưu ý: chủ sở hữu bảng vẫn có thể DROP TRIGGER; ở production hãy chạy ứng dụng bằng role chỉ có INSERT, SELECT
-- (tách khỏi role chạy Flyway) và đẩy log sang hệ thống ngoài (SIEM/WORM).
CREATE FUNCTION forbid_audit_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'payment_audit_log is append-only (% not allowed)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_no_update_delete
    BEFORE UPDATE OR DELETE ON payment_audit_log
    FOR EACH ROW EXECUTE FUNCTION forbid_audit_modification();

CREATE TRIGGER trg_audit_no_truncate
    BEFORE TRUNCATE ON payment_audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_audit_modification();
