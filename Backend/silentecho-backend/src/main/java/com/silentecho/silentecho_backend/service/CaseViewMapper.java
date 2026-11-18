package com.silentecho.silentecho_backend.service;

import com.silentecho.silentecho_backend.dto.CaseDtos.*;
import com.silentecho.silentecho_backend.entity.*;
import com.silentecho.silentecho_backend.repository.ComplaintEventRepository;
import com.silentecho.silentecho_backend.repository.ComplaintMessageRepository;
import com.silentecho.silentecho_backend.repository.EvidenceFileRepository;
import com.silentecho.silentecho_backend.security.CryptoService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts stored cases into API views, decrypting fields as it goes. Each AAD string binds a
 * ciphertext to its record, so a field copied to another case or file will not decrypt.
 * Reporter account IDs and message author IDs are never mapped into any view.
 */
@Component
public class CaseViewMapper {

    private final CryptoService crypto;
    private final ComplaintEventRepository events;
    private final ComplaintMessageRepository messages;
    private final EvidenceFileRepository evidence;

    public CaseViewMapper(CryptoService crypto, ComplaintEventRepository events,
                          ComplaintMessageRepository messages, EvidenceFileRepository evidence) {
        this.crypto = crypto;
        this.events = events;
        this.messages = messages;
        this.evidence = evidence;
    }

    public static String titleAad(String caseId) {
        return "complaint-title:" + caseId;
    }

    public static String descriptionAad(String caseId) {
        return "complaint-description:" + caseId;
    }

    public static String noteAad(String caseId) {
        return "event-note:" + caseId;
    }

    public static String messageAad(String caseId) {
        return "message:" + caseId;
    }

    public static String filenameAad(String caseId, String fileId) {
        return "evidence-name:" + caseId + ":" + fileId;
    }

    public static String evidenceBytesAad(String caseId, String fileId) {
        return "evidence-bytes:" + caseId + ":" + fileId;
    }

    public CaseSummary summary(Complaint complaint, String organisationName) {
        return new CaseSummary(
                complaint.getCaseId(),
                crypto.decryptText(complaint.getTitleCiphertext(), titleAad(complaint.getCaseId())),
                complaint.getCategory(),
                complaint.getUrgency(),
                complaint.getStatus(),
                organisationName,
                complaint.getCreatedAt(),
                complaint.getUpdatedAt());
    }

    public CaseDetail detail(Complaint complaint, String organisationName) {
        String caseId = complaint.getCaseId();
        List<TimelineEntry> timeline = events.findByComplaintIdOrderByCreatedAtAscIdAsc(complaint.getId()).stream()
                .map(event -> new TimelineEntry(
                        event.getEventType(),
                        event.getNoteCiphertext() == null ? null
                                : crypto.decryptText(event.getNoteCiphertext(), noteAad(caseId)),
                        event.getStatus(),
                        event.getUrgency(),
                        event.getCategory(),
                        event.getCreatedAt()))
                .toList();
        List<MessageView> conversation = messages.findByComplaintIdOrderByCreatedAtAscIdAsc(complaint.getId()).stream()
                .map(message -> new MessageView(
                        message.getSender(),
                        crypto.decryptText(message.getBodyCiphertext(), messageAad(caseId)),
                        message.getCreatedAt()))
                .toList();
        List<EvidenceView> files = evidence.findByComplaintIdOrderByCreatedAtAsc(complaint.getId()).stream()
                .map(file -> new EvidenceView(
                        file.getPublicId(),
                        crypto.decryptText(file.getFilenameCiphertext(), filenameAad(caseId, file.getPublicId())),
                        file.getContentType(),
                        file.getSizeBytes(),
                        file.getCreatedAt()))
                .toList();
        return new CaseDetail(
                caseId,
                crypto.decryptText(complaint.getTitleCiphertext(), titleAad(caseId)),
                crypto.decryptText(complaint.getDescriptionCiphertext(), descriptionAad(caseId)),
                complaint.getCategory(),
                complaint.getUrgency(),
                complaint.getStatus(),
                organisationName,
                complaint.getCreatedAt(),
                complaint.getUpdatedAt(),
                timeline,
                conversation,
                files);
    }
}
