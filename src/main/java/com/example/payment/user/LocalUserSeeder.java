package com.example.payment.user;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * CHỈ DÙNG LOCAL/TEST (profile "local"): tạo user mẫu trong Keycloak qua Admin API, thay cho mảng "users" trong realm JSON.
 * Idempotent: user đã có thì bỏ qua. Mật khẩu dưới đây là mật khẩu dev, không dùng ở môi trường thật.
 */
@Component
@Profile("local")
class LocalUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalUserSeeder.class);

    private record Seed(String username, String firstName, String password, Set<String> roles) {}

    private static final List<Seed> SEEDS = List.of(
            new Seed("admin.user", "Admin", "Admin@12345678", Set.of("payment-admin", "payment-operator", "payment-viewer")),
            new Seed("operator.user", "Operator", "Operator@12345678", Set.of("payment-operator", "payment-viewer")),
            new Seed("viewer.user", "Viewer", "Viewer@12345678", Set.of("payment-viewer")),
            new Seed("auditor.user", "Auditor", "Auditor@12345678", Set.of("auditor")));

    private final KeycloakAdminClient keycloak;

    LocalUserSeeder(KeycloakAdminClient keycloak) {
        this.keycloak = keycloak;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (Seed s : SEEDS) {
                boolean exists = keycloak.listUsers(s.username(), 0, 10).stream()
                        .anyMatch(u -> s.username().equals(u.username()));
                if (exists) {
                    continue;
                }
                // temporary=false: mật khẩu dev dùng được ngay, không bắt đổi
                keycloak.createUser(s.username(), s.username() + "@bank.local", s.firstName(), "User",
                        s.password(), false, s.roles());
                log.info("Seeded local user {}", s.username());
            }
        } catch (RuntimeException e) {
            // Keycloak chưa sẵn sàng/chưa import realm mới: không làm app chết
            log.warn("Skip seeding local users: {}", e.getMessage());
        }
    }
}
