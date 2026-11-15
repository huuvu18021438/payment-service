package com.example.payment.user;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {}

    /** Mật khẩu chỉ là mật khẩu tạm: người dùng bị buộc đổi ở lần đăng nhập đầu. Keycloak áp password policy của realm. */
    public record CreateUserRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @NotBlank @Size(max = 128) String temporaryPassword,
            @NotNull Set<String> roles) {}

    public record UpdateUserRequest(
            @Email @Size(max = 255) String email,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName) {}

    public record SetRolesRequest(@NotNull Set<String> roles) {}

    public record SetEnabledRequest(boolean enabled) {}

    public record ResetPasswordRequest(@NotBlank @Size(max = 128) String temporaryPassword) {}

    public record UserResponse(
            String id, String username, String email, String firstName, String lastName,
            boolean enabled, List<String> roles) {}
}
