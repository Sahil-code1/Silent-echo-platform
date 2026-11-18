package com.silentecho.silentecho_backend.security;

import com.silentecho.silentecho_backend.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class CurrentAccount {

    private CurrentAccount() {
    }

    public static Optional<AuthenticatedAccount> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedAccount account) {
            return Optional.of(account);
        }
        return Optional.empty();
    }

    public static AuthenticatedAccount require() {
        return find().orElseThrow(() -> new ApiException(401, "Sign in to continue."));
    }
}
