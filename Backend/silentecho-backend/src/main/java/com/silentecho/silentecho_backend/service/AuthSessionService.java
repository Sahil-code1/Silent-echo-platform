package com.silentecho.silentecho_backend.service;

import com.silentecho.silentecho_backend.entity.Account;
import com.silentecho.silentecho_backend.security.AuthenticatedAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Starts and ends server-side Spring Security sessions. Starting a session rotates the session ID
 * so an attacker-planted pre-login session cannot be reused after sign-in.
 */
@Service
public class AuthSessionService {

    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public void start(Account account, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
        AuthenticatedAccount principal = new AuthenticatedAccount(account.getId(), account.getAccountType());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getAccountType().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    /** Server-side logout: the session is invalidated, so the old cookie no longer works. */
    public void end(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
