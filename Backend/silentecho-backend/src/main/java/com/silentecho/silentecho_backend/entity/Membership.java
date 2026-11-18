package com.silentecho.silentecho_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "memberships", uniqueConstraints = @UniqueConstraint(
        name = "uk_membership_account_org", columnNames = {"account_id", "organisation_id"}))
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "organisation_id", nullable = false, length = 36)
    private String organisationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Membership() {
    }

    public Membership(String accountId, String organisationId, MembershipRole role, Instant createdAt) {
        this.accountId = accountId;
        this.organisationId = organisationId;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getOrganisationId() { return organisationId; }
    public MembershipRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
