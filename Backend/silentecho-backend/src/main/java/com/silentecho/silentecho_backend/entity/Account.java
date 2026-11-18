package com.silentecho.silentecho_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * An account is identified only by its private sign-in ID, which is stored as a keyed digest.
 * Accounts carry no name, email or phone number.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "sign_in_id_digest", nullable = false, unique = true, length = 64)
    private String signInIdDigest;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected Account() {
    }

    public Account(String id, AccountType accountType, String signInIdDigest, Instant createdAt) {
        this.id = id;
        this.accountType = accountType;
        this.signInIdDigest = signInIdDigest;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public AccountType getAccountType() { return accountType; }
    public String getSignInIdDigest() { return signInIdDigest; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
