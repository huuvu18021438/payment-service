package com.example.payment.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Chuyển JWT của Keycloak thành authorities của Spring Security:
 * <ul>
 *   <li>realm_access.roles  -> ROLE_{ROLE_IN_UPPER_CASE}, '-' đổi thành '_' (payment-admin -> ROLE_PAYMENT_ADMIN)</li>
 *   <li>scope               -> SCOPE_{scope} (ví dụ SCOPE_payment:write)</li>
 * </ul>
 * Principal name là preferred_username (nếu có) để audit dễ đọc; "sub" vẫn dùng để định danh dữ liệu.
 */
public class KeycloakJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.addAll(realmRoles(jwt));
        authorities.addAll(scopes(jwt));
        String principal = jwt.getClaimAsString("preferred_username");
        if (principal == null || principal.isBlank()) {
            principal = jwt.getSubject();
        }
        return new JwtAuthenticationToken(jwt, authorities, principal);
    }

    private Collection<GrantedAuthority> realmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map) || !(map.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .map(r -> "ROLE_" + r.toUpperCase(Locale.ROOT).replace('-', '_'))
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }

    private Collection<GrantedAuthority> scopes(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(scope.split("\\s+"))
                .<GrantedAuthority>map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                .toList();
    }
}
