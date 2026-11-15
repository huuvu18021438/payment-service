package com.example.payment.user;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Gọi Keycloak Admin REST API bằng service account của client bank-admin-api (client_credentials).
 * User/role nằm trong DB của Keycloak; app không tự lưu mật khẩu hay user.
 * Chỉ quản lý các role nghiệp vụ trong {@link #APP_ROLES}, không động tới role hệ thống của Keycloak.
 */
@Component
public class KeycloakAdminClient {

    public static final Set<String> APP_ROLES =
            Set.of("payment-admin", "payment-operator", "payment-viewer", "auditor");

    private static final String REALM_PATH = "/admin/realms/{realm}";

    public record KcUser(String id, String username, String email, String firstName, String lastName, Boolean enabled) {}

    private record KcRole(String id, String name) {}

    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("expires_in") long expiresIn) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record KcCredential(String type, String value, Boolean temporary) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record KcUserWrite(String username, String email, String firstName, String lastName,
                               Boolean enabled, Boolean emailVerified, List<KcCredential> credentials) {}

    private final RestClient rest;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public KeycloakAdminClient(
            RestClient.Builder builder,
            ObjectMapper json,
            @Value("${app.keycloak.admin.base-url}") String baseUrl,
            @Value("${app.keycloak.admin.realm}") String realm,
            @Value("${app.keycloak.admin.client-id}") String clientId,
            @Value("${app.keycloak.admin.client-secret}") String clientSecret) {
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.rest = builder.baseUrl(baseUrl)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {
                    HttpStatusCode code = res.getStatusCode();
                    // 400 (vd. vi phạm password policy) và 404/409 là lỗi của request -> trả lại; còn lại coi là lỗi hạ tầng
                    if (code.value() == 400 || code.value() == 404 || code.value() == 409) {
                        throw new ResponseStatusException(code, errorMessage(json, res.getBody()));
                    }
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity provider error");
                })
                .build();
    }

    // ------------------------------------------------------------------ users

    public List<KcUser> listUsers(String search, int first, int max) {
        return rest.get()
                .uri(b -> {
                    var u = b.path(REALM_PATH + "/users").queryParam("briefRepresentation", true)
                            .queryParam("first", first).queryParam("max", max);
                    if (search != null && !search.isBlank()) {
                        u.queryParam("search", search);
                    }
                    return u.build(realm);
                })
                .headers(h -> h.setBearerAuth(adminToken()))
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public KcUser getUser(String id) {
        return rest.get().uri(REALM_PATH + "/users/{id}", realm, id)
                .headers(h -> h.setBearerAuth(adminToken()))
                .retrieve().body(KcUser.class);
    }

    /**
     * Tạo user với mật khẩu (temporary=true: bắt đổi ở lần đăng nhập đầu), rồi gán role.
     * Trả về id (= claim "sub" trong token).
     */
    public String createUser(String username, String email, String firstName, String lastName,
                             String password, boolean temporary, Set<String> roles) {
        requireAppRoles(roles);
        var body = new KcUserWrite(username, email, firstName, lastName, true, false,
                List.of(new KcCredential("password", password, temporary)));
        var location = rest.post().uri(REALM_PATH + "/users", realm)
                .headers(h -> h.setBearerAuth(adminToken()))
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toBodilessEntity().getHeaders().getLocation();
        if (location == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity provider error");
        }
        String path = location.getPath();
        String id = path.substring(path.lastIndexOf('/') + 1);
        setRoles(id, roles);
        return id;
    }

    public void updateUser(String id, String email, String firstName, String lastName) {
        put(id, "", new KcUserWrite(null, email, firstName, lastName, null, null, null));
    }

    public void setEnabled(String id, boolean enabled) {
        put(id, "", new KcUserWrite(null, null, null, null, enabled, null, null));
        if (!enabled) {
            // Khóa user thì đá luôn các phiên đang đăng nhập
            rest.post().uri(REALM_PATH + "/users/{id}/logout", realm, id)
                    .headers(h -> h.setBearerAuth(adminToken())).retrieve().toBodilessEntity();
        }
    }

    /** temporary=true: user bị buộc đổi mật khẩu ở lần đăng nhập kế tiếp. */
    public void resetPassword(String id, String password, boolean temporary) {
        put(id, "/reset-password", new KcCredential("password", password, temporary));
    }

    // ------------------------------------------------------------------ roles

    public List<String> getRoles(String userId) {
        return roleMappings(userId).stream().map(KcRole::name).sorted().toList();
    }

    /** Đặt đúng tập role nghiệp vụ cho user: thêm role thiếu, gỡ role thừa (chỉ trong APP_ROLES). */
    public void setRoles(String userId, Set<String> wanted) {
        requireAppRoles(wanted);
        Set<String> current = new java.util.HashSet<>(getRoles(userId));
        List<KcRole> toAdd = new ArrayList<>();
        for (String name : wanted) {
            if (!current.contains(name)) {
                toAdd.add(realmRole(name));
            }
        }
        List<KcRole> toRemove = new ArrayList<>();
        for (String name : current) {
            if (APP_ROLES.contains(name) && !wanted.contains(name)) {
                toRemove.add(realmRole(name));
            }
        }
        if (!toAdd.isEmpty()) {
            rest.post().uri(REALM_PATH + "/users/{id}/role-mappings/realm", realm, userId)
                    .headers(h -> h.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON).body(toAdd)
                    .retrieve().toBodilessEntity();
        }
        if (!toRemove.isEmpty()) {
            rest.method(HttpMethod.DELETE).uri(REALM_PATH + "/users/{id}/role-mappings/realm", realm, userId)
                    .headers(h -> h.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON).body(toRemove)
                    .retrieve().toBodilessEntity();
        }
    }

    public static void requireAppRoles(Set<String> roles) {
        for (String r : roles) {
            if (!APP_ROLES.contains(r)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + r);
            }
        }
    }

    // ------------------------------------------------------------------ internals

    private List<KcRole> roleMappings(String userId) {
        return rest.get().uri(REALM_PATH + "/users/{id}/role-mappings/realm", realm, userId)
                .headers(h -> h.setBearerAuth(adminToken()))
                .retrieve().body(new ParameterizedTypeReference<>() {});
    }

    private KcRole realmRole(String name) {
        return rest.get().uri(REALM_PATH + "/roles/{name}", realm, name)
                .headers(h -> h.setBearerAuth(adminToken()))
                .retrieve().body(KcRole.class);
    }

    private void put(String id, String suffix, Object body) {
        rest.put().uri(REALM_PATH + "/users/{id}" + suffix, realm, id)
                .headers(h -> h.setBearerAuth(adminToken()))
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toBodilessEntity();
    }

    /** Token client_credentials được cache đến gần hết hạn. */
    private synchronized String adminToken() {
        if (token == null || Instant.now().isAfter(tokenExpiresAt.minusSeconds(30))) {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            var res = rest.post().uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(TokenResponse.class);
            token = res.accessToken();
            tokenExpiresAt = Instant.now().plusSeconds(res.expiresIn());
        }
        return token;
    }

    private static String errorMessage(ObjectMapper json, java.io.InputStream body) {
        try {
            var node = json.readTree(body);
            if (node != null) {
                for (String field : List.of("errorMessage", "error_description", "error")) {
                    if (node.hasNonNull(field)) {
                        return node.get(field).asText();
                    }
                }
            }
        } catch (IOException ignored) {
            // body không phải JSON -> dùng thông báo chung
        }
        return "Request rejected by identity provider";
    }
}
