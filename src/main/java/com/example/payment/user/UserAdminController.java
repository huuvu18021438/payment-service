package com.example.payment.user;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.payment.audit.AuditService;
import com.example.payment.user.KeycloakAdminClient.KcUser;
import com.example.payment.user.UserDtos.CreateUserRequest;
import com.example.payment.user.UserDtos.ResetPasswordRequest;
import com.example.payment.user.UserDtos.SetEnabledRequest;
import com.example.payment.user.UserDtos.SetRolesRequest;
import com.example.payment.user.UserDtos.UpdateUserRequest;
import com.example.payment.user.UserDtos.UserResponse;

import jakarta.validation.Valid;

/** Quản lý user/role (lưu trong DB của Keycloak). Chỉ PAYMENT_ADMIN; mọi thay đổi đều ghi audit (không ghi mật khẩu). */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('PAYMENT_ADMIN')")
public class UserAdminController {

    private static final int MAX_PAGE_SIZE = 100;

    private final KeycloakAdminClient keycloak;
    private final AuditService audit;

    public UserAdminController(KeycloakAdminClient keycloak, AuditService audit) {
        this.keycloak = keycloak;
        this.audit = audit;
    }

    @GetMapping
    public List<UserResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int first,
            @RequestParam(defaultValue = "20") int max) {
        int safeMax = Math.max(1, Math.min(max, MAX_PAGE_SIZE));
        return keycloak.listUsers(search, Math.max(0, first), safeMax).stream()
                .map(u -> toResponse(u, null)).toList();
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable String id) {
        return toResponse(keycloak.getUser(id), keycloak.getRoles(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        String id = keycloak.createUser(req.username(), req.email(), req.firstName(), req.lastName(),
                req.temporaryPassword(), true, req.roles());
        audit.record(null, "USER_CREATED", "userId=" + id + " username=" + req.username() + " roles=" + sorted(req.roles()));
        return get(id);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest req) {
        keycloak.updateUser(id, req.email(), req.firstName(), req.lastName());
        audit.record(null, "USER_UPDATED", "userId=" + id);
        return get(id);
    }

    @PutMapping("/{id}/roles")
    public UserResponse setRoles(@PathVariable String id, @Valid @RequestBody SetRolesRequest req) {
        keycloak.setRoles(id, req.roles());
        audit.record(null, "USER_ROLES_CHANGED", "userId=" + id + " roles=" + sorted(req.roles()));
        return get(id);
    }

    @PutMapping("/{id}/enabled")
    public UserResponse setEnabled(@PathVariable String id, @RequestBody SetEnabledRequest req) {
        keycloak.setEnabled(id, req.enabled());
        audit.record(null, "USER_ENABLED_CHANGED", "userId=" + id + " enabled=" + req.enabled());
        return get(id);
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable String id, @Valid @RequestBody ResetPasswordRequest req) {
        keycloak.resetPassword(id, req.temporaryPassword(), true);
        audit.record(null, "USER_PASSWORD_RESET", "userId=" + id);
    }

    private static UserResponse toResponse(KcUser u, List<String> roles) {
        return new UserResponse(u.id(), u.username(), u.email(), u.firstName(), u.lastName(),
                Boolean.TRUE.equals(u.enabled()), roles);
    }

    private static List<String> sorted(Set<String> roles) {
        return roles.stream().sorted().toList();
    }
}
