package com.example.payment.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Tên người dùng hiện tại (preferred_username hoặc sub) để ghi created_by / updated_by. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String name() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getName() != null ? auth.getName() : "system";
    }
}
