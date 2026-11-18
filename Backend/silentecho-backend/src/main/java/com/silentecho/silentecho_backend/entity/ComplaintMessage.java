package com.silentecho.silentecho_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A two-way message. {@code authorAccountId} is set on responder messages for internal
 * audit only. It is never returned to the reporter or to other responders.
 */
@Entity
@Table(name = "complaint_messages")
public class ComplaintMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageSender sender;

    @Column(name = "author_account_id", length = 36)
    private String authorAccountId;

    @Column(name = "body_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String bodyCiphertext;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ComplaintMessage() {
    }

    public ComplaintMessage(Long complaintId, MessageSender sender, String authorAccountId,
                            String bodyCiphertext, Instant createdAt) {
        this.complaintId = complaintId;
        this.sender = sender;
        this.authorAccountId = authorAccountId;
        this.bodyCiphertext = bodyCiphertext;
        this.createdAt = createdAt;
    }

    public MessageSender getSender() { return sender; }
    public String getBodyCiphertext() { return bodyCiphertext; }
    public Instant getCreatedAt() { return createdAt; }
}
