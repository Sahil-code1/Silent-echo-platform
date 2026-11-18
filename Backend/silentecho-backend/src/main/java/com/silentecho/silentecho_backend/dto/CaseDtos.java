package com.silentecho.silentecho_backend.dto;

import com.silentecho.silentecho_backend.entity.ComplaintStatus;
import com.silentecho.silentecho_backend.entity.EventType;
import com.silentecho.silentecho_backend.entity.MessageSender;
import com.silentecho.silentecho_backend.entity.Priority;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class CaseDtos {

    private CaseDtos() {
    }

    public record CreateComplaintRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 60) String category,
            @NotNull Priority urgency,
            @NotBlank @Size(max = 5000) String description,
            @NotBlank @Size(max = 36) String organisationId,
            @AssertTrue(message = "Please confirm the report can be shared with the chosen organisation.")
            boolean consentShared) {
    }

    /**
     * Summary for lists. Reporter identity is never included. The caller's own account ID is never echoed back.
     */
    public record CaseSummary(
            String caseId,
            String title,
            String category,
            Priority urgency,
            ComplaintStatus status,
            String organisationName,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record TimelineEntry(
            EventType type,
            String note,
            ComplaintStatus status,
            Priority urgency,
            String category,
            Instant at) {
    }

    public record MessageView(MessageSender sender, String body, Instant sentAt) {
    }

    public record EvidenceView(String fileId, String filename, String contentType, long sizeBytes, Instant uploadedAt) {
    }

    public record CaseDetail(
            String caseId,
            String title,
            String description,
            String category,
            Priority urgency,
            ComplaintStatus status,
            String organisationName,
            Instant createdAt,
            Instant updatedAt,
            List<TimelineEntry> timeline,
            List<MessageView> messages,
            List<EvidenceView> evidence) {
    }

    public record SendMessageRequest(@NotBlank @Size(max = 2000) String body) {
    }

    /** Every field is optional. Only the fields present are changed. */
    public record UpdateCaseRequest(
            ComplaintStatus status,
            Priority urgency,
            @Size(max = 60) String category,
            @Size(max = 2000) String actionNote) {
    }
}
