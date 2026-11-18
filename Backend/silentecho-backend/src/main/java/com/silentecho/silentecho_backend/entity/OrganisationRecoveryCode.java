package com.silentecho.silentecho_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** One-time secret that lets an organisation create a replacement administrator account. */
@Entity
@Table(name = "organisation_recovery_codes")
public class OrganisationRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false, length = 36)
    private String organisationId;

    @Column(name = "code_digest", nullable = false, unique = true, length = 64)
    private String codeDigest;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected OrganisationRecoveryCode() {
    }

    public OrganisationRecoveryCode(String organisationId, String codeDigest, Instant createdAt) {
        this.organisationId = organisationId;
        this.codeDigest = codeDigest;
        this.createdAt = createdAt;
    }

    public boolean isUsable() {
        return usedAt == null;
    }

    public void markUsed(Instant when) {
        this.usedAt = when;
    }
}
