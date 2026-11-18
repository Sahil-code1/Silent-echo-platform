package com.silentecho.silentecho_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** One entry in a case timeline. Responder action notes are stored encrypted. */
@Entity
@Table(name = "complaint_events")
public class ComplaintEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "note_ciphertext", columnDefinition = "TEXT")
    private String noteCiphertext;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ComplaintStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Priority urgency;

    @Column(length = 60)
    private String category;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ComplaintEvent() {
    }

    public ComplaintEvent(Long complaintId, EventType eventType, String noteCiphertext,
                          ComplaintStatus status, Priority urgency, String category, Instant createdAt) {
        this.complaintId = complaintId;
        this.eventType = eventType;
        this.noteCiphertext = noteCiphertext;
        this.status = status;
        this.urgency = urgency;
        this.category = category;
        this.createdAt = createdAt;
    }

    public EventType getEventType() { return eventType; }
    public String getNoteCiphertext() { return noteCiphertext; }
    public ComplaintStatus getStatus() { return status; }
    public Priority getUrgency() { return urgency; }
    public String getCategory() { return category; }
    public Instant getCreatedAt() { return createdAt; }
}
