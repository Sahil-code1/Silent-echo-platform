package com.silentecho.silentecho_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** A single-use team invitation. Only the keyed digest of the code is stored. */
@Entity
@Table(name = "invitations")
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false, length = 36)
    private String organisationId;

    @Column(name = "code_digest", nullable = false, unique = true, length = 64)
    private String codeDigest;

    @Column(name = "created_by_account_id", nullable = false, length = 36)
    private String createdByAccountId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected Invitation() {
    }

    public Invitation(String organisationId, String codeDigest, String createdByAccountId,
                      Instant createdAt, Instant expiresAt) {
        this.organisationId = organisationId;
        this.codeDigest = codeDigest;
        this.createdByAccountId = createdByAccountId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant when) { this.usedAt = when; }

    public String getOrganisationId() { return organisationId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}
