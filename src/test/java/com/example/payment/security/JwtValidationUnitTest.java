package com.example.payment.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtValidationUnitTest {

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("user-sub-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
    }

    @Test
    void audienceValidator_rejectsTokenWithoutExpectedAudience() {
        var validator = new AudienceValidator("payment-service");

        assertThat(validator.validate(jwt().claim("aud", List.of("account")).build()).hasErrors()).isTrue();
        assertThat(validator.validate(jwt().claim("sub", "x").build()).hasErrors()).isTrue();
    }

    @Test
    void audienceValidator_acceptsTokenWithExpectedAudience() {
        var validator = new AudienceValidator("payment-service");

        assertThat(validator.validate(jwt().claim("aud", List.of("account", "payment-service")).build()).hasErrors())
                .isFalse();
    }

    @Test
    void converter_mapsRealmRolesAndScopes() {
        Jwt token = jwt()
                .claim("preferred_username", "operator.user")
                .claim("realm_access", Map.of("roles", List.of("payment-operator", "payment-viewer")))
                .claim("scope", "openid payment:read payment:write")
                .build();

        var auth = new KeycloakJwtAuthConverter().convert(token);

        assertThat(auth.getName()).isEqualTo("operator.user");
        assertThat(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()))
                .contains("ROLE_PAYMENT_OPERATOR", "ROLE_PAYMENT_VIEWER", "SCOPE_payment:read", "SCOPE_payment:write");
    }

    @Test
    void converter_fallsBackToSubjectAndToleratesMissingClaims() {
        var auth = new KeycloakJwtAuthConverter().convert(jwt().claim("scope", "").build());

        assertThat(auth.getName()).isEqualTo("user-sub-1");
        assertThat(auth.getAuthorities()).isEmpty();
    }
}
