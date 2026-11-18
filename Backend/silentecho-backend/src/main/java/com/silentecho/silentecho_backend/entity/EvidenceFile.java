package com.silentecho.silentecho_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Metadata for an uploaded file. The bytes are AES-GCM encrypted on disk outside the web root
 * under {@code storageKey}. The original filename is stored as ciphertext.
 */
@Entity
@Table(name = "evidence_files")
public class EvidenceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Column(name = "filename_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String filenameCiphertext;

    @Column(name = "content_type", nullable = false, length = 60)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, unique = true, length = 36)
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EvidenceFile() {
    }

    public EvidenceFile(String publicId, Long complaintId, String filenameCiphertext, String contentType,
                        long sizeBytes, String storageKey, Instant createdAt) {
        this.publicId = publicId;
        this.complaintId = complaintId;
        this.filenameCiphertext = filenameCiphertext;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.createdAt = createdAt;
    }

    public String getPublicId() { return publicId; }
    public Long getComplaintId() { return complaintId; }
    public String getFilenameCiphertext() { return filenameCiphertext; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStorageKey() { return storageKey; }
    public Instant getCreatedAt() { return createdAt; }
}
