package com.silentecho.silentecho_backend.controller;

import com.silentecho.silentecho_backend.dto.AuthDtos.*;
import com.silentecho.silentecho_backend.entity.Account;
import com.silentecho.silentecho_backend.entity.AccountType;
import com.silentecho.silentecho_backend.security.CurrentAccount;
import com.silentecho.silentecho_backend.security.LoginRateLimiter;
import com.silentecho.silentecho_backend.service.AccountService;
import com.silentecho.silentecho_backend.service.AuditService;
import com.silentecho.silentecho_backend.service.AuthSessionService;
import com.silentecho.silentecho_backend.service.OrganisationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Pseudonymous registration, sign-in, sign-out and session inspection. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accountService;
    private final AuthSessionService sessions;
    private final OrganisationService organisations;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;

    public AuthController(AccountService accountService, AuthSessionService sessions,
                          OrganisationService organisations, LoginRateLimiter rateLimiter, AuditService audit) {
        this.accountService = accountService;
        this.sessions = sessions;
        this.organisations = organisations;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    /** Issues the CSRF token the SPA must echo in the X-CSRF-TOKEN header on every write. */
    @GetMapping("/csrf")
    public CsrfResponse csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            throw new IllegalStateException("CSRF token unavailable");
        }
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/personal/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountCreatedResponse registerPersonal(HttpServletRequest request, HttpServletResponse response) {
        AccountService.NewAccount created = accountService.createAccount(AccountType.PERSONAL);
        sessions.start(created.account(), request, response);
        audit.record(created.account().getId(), null, null, "PERSONAL_ACCOUNT_CREATED");
        return new AccountCreatedResponse(created.signInId(), AccountType.PERSONAL, List.of(), null);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body,
                                   HttpServletRequest request, HttpServletResponse response) {
        String clientKey = request.getRemoteAddr();
        if (rateLimiter.isBlocked(clientKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Too many failed attempts. Wait 15 minutes and try again."));
        }
        Optional<Account> match = accountService.authenticate(body.signInId());
        if (match.isEmpty()) {
            rateLimiter.recordFailure(clientKey);
            audit.record(null, null, null, "LOGIN_FAILED");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Sign-in failed. Check your private sign-in ID and try again."));
        }
        Account account = match.get();
        if (account.getAccountType() != body.workspace()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "This sign-in ID belongs to the other workspace. Use the other sign-in page."));
        }
        rateLimiter.recordSuccess(clientKey);
        sessions.start(account, request, response);
        audit.record(account.getId(), null, null, "LOGIN_SUCCEEDED");
        return ResponseEntity.ok(new SessionResponse(true, account.getAccountType(),
                organisations.membershipsOf(account.getId())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        CurrentAccount.find().ifPresent(p -> audit.record(p.accountId(), null, null, "LOGOUT"));
        sessions.end(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public SessionResponse me() {
        return CurrentAccount.find()
                .map(p -> new SessionResponse(true, p.accountType(), organisations.membershipsOf(p.accountId())))
                .orElse(SessionResponse.anonymous());
    }
}
