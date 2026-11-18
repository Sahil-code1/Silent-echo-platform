package com.silentecho.silentecho_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A case. {@code caseId} is a random UUID used as a reference only; it is never an
 * authenticator. Title and description are stored as AES-256-GCM ciphertext.
 * {@code reporterAccountId} is internal and is never returned to organisation users.
 */
@Entity
@Table(name = "complaints")
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false, unique = true, length = 36)
    private String caseId;

    @Column(name = "reporter_account_id", nullable = false, length = 36)
    private String reporterAccountId;

    @Column(name = "organisation_id", nullable = false, length = 36)
    private String organisationId;

    @Column(nullable = false, length = 60)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority urgency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplaintStatus status;

    @Column(name = "title_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String titleCiphertext;

    @Column(name = "description_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String descriptionCiphertext;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Complaint() {
    }

    public Complaint(String caseId, String reporterAccountId, String organisationId, String category,
                     Priority urgency, String titleCiphertext, String descriptionCiphertext, Instant now) {
        this.caseId = caseId;
        this.reporterAccountId = reporterAccountId;
        this.organisationId = organisationId;
        this.category = category;
        this.urgency = urgency;
        this.status = ComplaintStatus.SUBMITTED;
        this.titleCiphertext = titleCiphertext;
        this.descriptionCiphertext = descriptionCiphertext;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getCaseId() { return caseId; }
    public String getReporterAccountId() { return reporterAccountId; }
    public String getOrganisationId() { return organisationId; }
    public String getCategory() { return category; }
    public Priority getUrgency() { return urgency; }
    public ComplaintStatus getStatus() { return status; }
    public String getTitleCiphertext() { return titleCiphertext; }
    public String getDescriptionCiphertext() { return descriptionCiphertext; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCategory(String category) { this.category = category; }
    public void setUrgency(Priority urgency) { this.urgency = urgency; }
    public void setStatus(ComplaintStatus status) { this.status = status; }
    public void touch(Instant when) { this.updatedAt = when; }
}
