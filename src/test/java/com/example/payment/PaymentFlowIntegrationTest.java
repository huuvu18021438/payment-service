package com.example.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end: Postgres thật + Keycloak thật (import keycloak/bank-realm.json) + token thật.
 * Cần Docker đang chạy.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PaymentFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.0")
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withEnv("KC_BATCH_CLIENT_SECRET", "dev-only-change-me-batch-secret")
            .withEnv("KC_ADMIN_CLIENT_SECRET", "dev-only-change-me-admin-secret")
            .withCopyFileToContainer(MountableFile.forHostPath("keycloak/bank-realm.json"),
                    "/opt/keycloak/data/import/bank-realm.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/bank").forPort(8080).withStartupTimeout(Duration.ofMinutes(3)));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/bank");
        r.add("app.keycloak.admin.base-url", () -> "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080));
    }

    /**
     * Realm thật không còn user và tắt password grant cho bank-web. User do LocalUserSeeder (profile "local") tạo qua Admin API;
     * riêng test này bật password grant tạm thời trên container Keycloak để lấy token không cần trình duyệt.
     */
    @BeforeAll
    static void enablePasswordGrantForTest() throws Exception {
        var mapper = new ObjectMapper();
        var http = HttpClient.newHttpClient();
        String base = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);

        var tokenRes = http.send(HttpRequest.newBuilder(URI.create(base + "/realms/master/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=password&client_id=admin-cli&username=admin&password=admin"))
                .build(), HttpResponse.BodyHandlers.ofString());
        String admin = mapper.readTree(tokenRes.body()).get("access_token").asText();

        var clients = http.send(HttpRequest.newBuilder(URI.create(base + "/admin/realms/bank/clients?clientId=bank-web"))
                .header("Authorization", "Bearer " + admin).GET().build(), HttpResponse.BodyHandlers.ofString());
        String id = mapper.readTree(clients.body()).get(0).get("id").asText();

        var update = http.send(HttpRequest.newBuilder(URI.create(base + "/admin/realms/bank/clients/" + id))
                .header("Authorization", "Bearer " + admin).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"directAccessGrantsEnabled\":true}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(update.statusCode()).as(update.body()).isEqualTo(204);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    // ------------------------------------------------------------------ helpers

    private String token(String user, String password, String scope) throws Exception {
        String form = Map.of("grant_type", "password", "client_id", "bank-web",
                        "username", user, "password", password, "scope", scope)
                .entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b).orElseThrow();
        var req = HttpRequest.newBuilder(URI.create("http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080)
                        + "/realms/bank/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        var res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        return json.readTree(res.body()).get("access_token").asText();
    }

    private String subOf(String accessToken) throws Exception {
        byte[] payload = Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]);
        return json.readTree(payload).get("sub").asText();
    }

    private static String bearer(String t) {
        return "Bearer " + t;
    }

    private static String body(String customerId, String amount) {
        return "{\"customerId\":\"" + customerId + "\",\"amount\":" + amount + ",\"currency\":\"VND\",\"description\":\"it\"}";
    }

    // ------------------------------------------------------------------ test

    @Test
    void openApi_isPublicAndDeclaresKeycloakOAuth2() throws Exception {
        String issuer = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/bank";
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.keycloak.type").value("oauth2"))
                .andExpect(jsonPath("$.components.securitySchemes.keycloak.flows.authorizationCode.authorizationUrl")
                        .value(issuer + "/protocol/openid-connect/auth"))
                .andExpect(jsonPath("$.paths['/api/payments']").exists());
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oauth2RedirectUrl").exists());
    }

    @Test
    void fullPaymentFlow_withRealKeycloakTokens() throws Exception {
        String viewer = token("viewer.user", "Viewer@12345678", "openid");
        String operator = token("operator.user", "Operator@12345678", "openid payment:write");
        String adminNoRefund = token("admin.user", "Admin@12345678", "openid payment:write");
        String admin = token("admin.user", "Admin@12345678", "openid payment:write payment:refund");
        String auditor = token("auditor.user", "Auditor@12345678", "openid");
        String viewerSub = subOf(viewer);

        // Không token -> 401
        mvc.perform(get("/api/payments")).andExpect(status().isUnauthorized());

        // Viewer không được tạo giao dịch (role không đủ) -> 403
        mvc.perform(post("/api/payments").header("Authorization", bearer(viewer)).header("Idempotency-Key", "it-viewer")
                        .contentType(MediaType.APPLICATION_JSON).content(body(viewerSub, "5")))
                .andExpect(status().isForbidden());

        // Operator tạo giao dịch cho khách (viewer.user) -> 201, createdBy = operator.user
        MvcResult created = mvc.perform(post("/api/payments").header("Authorization", bearer(operator))
                        .header("Idempotency-Key", "it-key-1").contentType(MediaType.APPLICATION_JSON)
                        .content(body(viewerSub, "125000.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value("operator.user"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn();
        JsonNode payment = json.readTree(created.getResponse().getContentAsString());
        String id = payment.get("id").asText();

        // Gửi lại cùng Idempotency-Key + cùng nội dung -> 200, cùng id
        mvc.perform(post("/api/payments").header("Authorization", bearer(operator))
                        .header("Idempotency-Key", "it-key-1").contentType(MediaType.APPLICATION_JSON)
                        .content(body(viewerSub, "125000.50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        // Cùng key nhưng nội dung khác -> 409
        mvc.perform(post("/api/payments").header("Authorization", bearer(operator))
                        .header("Idempotency-Key", "it-key-1").contentType(MediaType.APPLICATION_JSON)
                        .content(body(viewerSub, "999")))
                .andExpect(status().isConflict());

        // Khách (viewer) xem được giao dịch của mình
        mvc.perform(get("/api/payments/{id}", id).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(viewerSub));

        // Giao dịch của người khác: viewer không thấy (404), operator thấy
        MvcResult other = mvc.perform(post("/api/payments").header("Authorization", bearer(operator))
                        .header("Idempotency-Key", "it-key-2").contentType(MediaType.APPLICATION_JSON)
                        .content(body("someone-else", "1")))
                .andExpect(status().isCreated()).andReturn();
        String otherId = json.readTree(other.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/payments/{id}", otherId).header("Authorization", bearer(viewer)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/payments/{id}", otherId).header("Authorization", bearer(operator)))
                .andExpect(status().isOk());

        // Danh sách của viewer chỉ chứa giao dịch của chính họ
        mvc.perform(get("/api/payments").header("Authorization", bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));

        // Refund: operator -> 403; admin thiếu scope refund -> 403; admin đủ scope -> 200; refund lần 2 -> 409
        mvc.perform(post("/api/payments/{id}/refund", id).header("Authorization", bearer(operator)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/{id}/refund", id).header("Authorization", bearer(adminNoRefund)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/{id}/refund", id).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.updatedBy").value("admin.user"));
        mvc.perform(post("/api/payments/{id}/refund", id).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());

        // Audit log: auditor xem được, viewer/operator thì không
        mvc.perform(get("/api/audit-logs").header("Authorization", bearer(viewer)))
                .andExpect(status().isForbidden());
        MvcResult logs = mvc.perform(get("/api/audit-logs?size=50").header("Authorization", bearer(auditor)))
                .andExpect(status().isOk()).andReturn();
        String logBody = logs.getResponse().getContentAsString();
        assertThat(logBody).contains("PAYMENT_CREATED", "PAYMENT_REFUNDED", "PAYMENT_VIEWED",
                "operator.user", "admin.user", "viewer.user");
        assertThat(logBody).doesNotContain(operator); // không bao giờ log token
    }
}
