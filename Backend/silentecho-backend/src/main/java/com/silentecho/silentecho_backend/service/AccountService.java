package com.silentecho.silentecho_backend.service;

import com.silentecho.silentecho_backend.entity.Account;
import com.silentecho.silentecho_backend.entity.AccountType;
import com.silentecho.silentecho_backend.repository.AccountRepository;
import com.silentecho.silentecho_backend.security.SignInIdGenerator;
import com.silentecho.silentecho_backend.security.SignInIdHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates accounts and verifies sign-in IDs. A raw ID exists only in the return value of
 * {@link #createAccount}, which the caller shows once. It is never persisted.
 */
@Service
public class AccountService {

    /** The raw sign-in ID is returned once. Callers must not store or log it. */
    public record NewAccount(Account account, String signInId) {
    }

    private final AccountRepository accounts;
    private final SignInIdGenerator generator;
    private final SignInIdHasher hasher;
    private final Clock clock;

    public AccountService(AccountRepository accounts, SignInIdGenerator generator,
                          SignInIdHasher hasher, Clock clock) {
        this.accounts = accounts;
        this.generator = generator;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Transactional
    public NewAccount createAccount(AccountType type) {
        String signInId = generator.generate();
        Account account = new Account(
                UUID.randomUUID().toString(),
                type,
                hasher.digestSignInId(signInId),
                clock.instant());
        accounts.save(account);
        return new NewAccount(account, signInId);
    }

    @Transactional
    public Optional<Account> authenticate(String rawSignInId) {
        if (rawSignInId == null) {
            return Optional.empty();
        }
        String candidate = rawSignInId.trim();
        if (!SignInIdGenerator.isWellFormed(candidate)) {
            return Optional.empty();
        }
        String digest = hasher.digestSignInId(candidate);
        Optional<Account> found = accounts.findBySignInIdDigest(digest)
                .filter(account -> SignInIdHasher.constantTimeEquals(digest, account.getSignInIdDigest()));
        found.ifPresent(account -> account.setLastLoginAt(clock.instant()));
        return found;
    }
}
