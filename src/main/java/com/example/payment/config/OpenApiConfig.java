package com.example.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger UI đăng nhập qua Keycloak bằng Authorization Code + PKCE (client public "bank-web").
 * Authorization/token URL được trình duyệt gọi trực tiếp nên dùng issuer mà trình duyệt truy cập được.
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "keycloak";

    @Bean
    OpenAPI paymentOpenApi(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        var flow = new OAuthFlow()
                .authorizationUrl(issuer + "/protocol/openid-connect/auth")
                .tokenUrl(issuer + "/protocol/openid-connect/token")
                .scopes(new Scopes()
                        .addString("openid", "OpenID Connect")
                        .addString("payment:read", "Xem giao dịch")
                        .addString("payment:write", "Tạo giao dịch")
                        .addString("payment:refund", "Hoàn tiền"));

        return new OpenAPI()
                .info(new Info().title("Payment Service API").version("1.0.0")
                        .description("Bảo mật bằng OAuth2 / Keycloak (realm bank). Dùng nút Authorize để đăng nhập."))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme().type(SecurityScheme.Type.OAUTH2).flows(new OAuthFlows().authorizationCode(flow))))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
