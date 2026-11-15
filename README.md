# payment-service

Spring Boot 3.5 / Java 25 – dịch vụ thanh toán, bảo mật bằng **OAuth2 + Keycloak** (payment-service là *Resource Server* xác thực JWT).

## Kiến trúc bảo mật

```
Client ──(Auth Code + PKCE / Client Credentials)──► Keycloak (realm "bank")
   │                                                     │ access token (JWT)
   └──────────── Authorization: Bearer <JWT> ──────────► payment-service
                                  kiểm tra: chữ ký (JWKS) · issuer · hạn dùng · audience=payment-service
                                  rồi áp role (ROLE_*) + scope (SCOPE_*) + quyền theo dữ liệu
```

| Role Keycloak | Quyền |
|---|---|
| `payment-viewer` | Xem giao dịch **của chính mình** |
| `payment-operator` | Tạo giao dịch, xem tất cả |
| `payment-admin` | Như operator + hoàn tiền (refund) + xem audit |
| `auditor` | Xem `/api/audit-logs` |

Mỗi API cần **cả role lẫn scope** (`payment:read`, `payment:write`, `payment:refund`).

| API | Yêu cầu |
|---|---|
| `POST /api/payments` (header `Idempotency-Key`) | operator/admin + `payment:write` |
| `GET /api/payments`, `GET /api/payments/{id}` | viewer/operator/admin + `payment:read` |
| `POST /api/payments/{id}/refund` | admin + `payment:refund` |
| `GET /api/audit-logs` | auditor/admin |

## Chạy local

Yêu cầu: JDK 25, Docker. (Có sẵn Maven Wrapper.)

```powershell
copy .env.example .env        # lần đầu: secret local nằm trong .env (không commit)
docker compose up -d          # Postgres (payment), Postgres (keycloak), Keycloak :8180 (tự import realm bank)
.\mvnw.cmd spring-boot:run    # service tại http://localhost:8080
```

> Windows: nếu đường dẫn user có dấu cách, `mvnw.cmd` lỗi – đặt `JAVA_HOME` và `MAVEN_USER_HOME`
> bằng đường dẫn ngắn 8.3 (vd `C:\Users\NGUYEN~1\...`).
> Múi giờ JVM được đặt `Asia/Ho_Chi_Minh` trong code vì PostgreSQL 17 từ chối tên cũ `Asia/Saigon`.

Keycloak Admin Console: http://localhost:8180 (admin / admin – **chỉ local**).

### Tài khoản test (chỉ dev/CI – xem `keycloak/bank-realm.json`)

| User | Mật khẩu | Role |
|---|---|---|
| `admin.user` | `Admin@12345678` | admin, operator, viewer |
| `operator.user` | `Operator@12345678` | operator, viewer |
| `viewer.user` | `Viewer@12345678` | viewer |
| `auditor.user` | `Auditor@12345678` | auditor |

Client: `bank-web` (public, Authorization Code + PKCE), `bank-backend-batch` (Client Credentials, secret dev `dev-only-change-me-batch-secret`).

## Thử bằng Swagger UI (đăng nhập qua Keycloak)

1. Mở http://localhost:8080/swagger-ui.html
2. Bấm **Authorize** → tick các scope cần (vd `payment:write`) → **Authorize**.
   (Client `bank-web` đã điền sẵn, PKCE tự bật; không cần client secret.)
3. Trang Keycloak hiện ra → đăng nhập, ví dụ `operator.user`.
4. Gọi API từ Swagger UI – token được gắn tự động.

Muốn thử refund: đăng nhập `admin.user` và tick scope `payment:refund`.

## Thử bằng curl

```powershell
# Lấy token (password grant chỉ để test local; production tắt, dùng Auth Code + PKCE)
$tok = (Invoke-RestMethod -Method Post http://localhost:8180/realms/bank/protocol/openid-connect/token -Body @{
  grant_type='password'; client_id='bank-web'; username='operator.user'; password='Operator@12345678'
  scope='openid payment:write' }).access_token

# Tạo giao dịch (customerId = "sub" của khách hàng trong Keycloak)
Invoke-RestMethod -Method Post http://localhost:8080/api/payments `
  -Headers @{Authorization="Bearer $tok"; 'Idempotency-Key'='demo-001'} `
  -ContentType 'application/json' `
  -Body '{"customerId":"<sub-cua-khach>","amount":125000.50,"currency":"VND","description":"demo"}'
```

Không có token → `401`; sai role/scope → `403`.

## Test

```powershell
.\mvnw.cmd test
```

- `JwtValidationUnitTest` – audience validator, ánh xạ role/scope
- `PaymentSecurityWebMvcTest` – 401/403/2xx theo role + scope (không cần Docker)
- `PaymentFlowIntegrationTest` – Postgres + Keycloak thật (Testcontainers, **cần Docker**), token thật

## Production (tóm tắt – chi tiết xem plan hardening)

- `SPRING_PROFILES_ACTIVE=prod` tắt Swagger/OpenAPI.
- `bank-realm.json` chỉ dành cho dev/CI. Production quản lý realm bằng IaC (Terraform / keycloak-config-cli),
  user lấy từ LDAP/AD hoặc IdP của doanh nghiệp; không dùng user/mật khẩu/secret trong file này.
- Tắt `directAccessGrantsEnabled` của `bank-web`, đổi redirect URI sang domain thật, secret lấy từ Vault/biến môi trường.
- Chạy Keycloak với `start` + HTTPS + hostname cố định, DB riêng, ≥2 node; TLS ở reverse proxy/gateway.
- Cấu hình qua biến môi trường: `KEYCLOAK_ISSUER`, `APP_SECURITY_AUDIENCE`, `APP_CORS_ORIGINS`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
