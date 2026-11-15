package com.example.payment.auth;

import java.util.List;
import java.util.Objects;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.payment.security.KeycloakJwtAuthConverter;

/**
 * BE là resource server stateless: SPA tự đăng nhập với Keycloak (Authorization Code + PKCE, public client)
 * rồi gọi API bằng Bearer token. Thiếu/hết hạn token -> 401 để SPA chuyển sang trang đăng nhập.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record MeResponse(String sub, String username, String email, List<String> authorities) {}

    /** Thông tin người đăng nhập + roles/scopes lấy từ access token (đã validate chữ ký/issuer/audience). */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        List<String> authorities = Objects.requireNonNull(new KeycloakJwtAuthConverter().convert(jwt)).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).sorted().toList();
        return new MeResponse(jwt.getSubject(), jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"), authorities);
    }
}
