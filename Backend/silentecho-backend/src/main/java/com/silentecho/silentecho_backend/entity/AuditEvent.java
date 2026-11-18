package com.silentecho.silentecho_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Records who did what, and when, without report content, sign-in IDs, tokens or IP addresses.
 * The actor is an internal account ID, kept for accountability.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_account_id", length = 36)
    private String actorAccountId;

    @Column(name = "organisation_id", length = 36)
    private String organisationId;

    @Column(name = "case_id", length = 36)
    private String caseId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(String actorAccountId, String organisationId, String caseId, String action, Instant createdAt) {
        this.actorAccountId = actorAccountId;
        this.organisationId = organisationId;
        this.caseId = caseId;
        this.action = action;
        this.createdAt = createdAt;
    }

    public String getAction() { return action; }
    public Instant getCreatedAt() { return createdAt; }
}
