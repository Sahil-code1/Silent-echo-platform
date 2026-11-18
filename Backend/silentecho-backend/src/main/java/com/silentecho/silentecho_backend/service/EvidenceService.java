package com.silentecho.silentecho_backend.service;

import com.silentecho.silentecho_backend.dto.CaseDtos.EvidenceView;
import com.silentecho.silentecho_backend.entity.*;
import com.silentecho.silentecho_backend.evidence.EvidenceStorage;
import com.silentecho.silentecho_backend.evidence.EvidenceValidator;
import com.silentecho.silentecho_backend.exception.ApiException;
import com.silentecho.silentecho_backend.repository.ComplaintEventRepository;
import com.silentecho.silentecho_backend.repository.EvidenceFileRepository;
import com.silentecho.silentecho_backend.security.AuthenticatedAccount;
import com.silentecho.silentecho_backend.security.CaseAccess;
import com.silentecho.silentecho_backend.security.CryptoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Evidence upload and download. Bytes are encrypted before they reach disk. */
@Service
public class EvidenceService {

    public record Download(String filename, String contentType, byte[] bytes) {
    }

    private final CaseAccess access;
    private final EvidenceFileRepository evidence;
    private final ComplaintEventRepository events;
    private final EvidenceValidator validator;
    private final EvidenceStorage storage;
    private final CryptoService crypto;
    private final AuditService audit;
    private final Clock clock;

    public EvidenceService(CaseAccess access, EvidenceFileRepository evidence, ComplaintEventRepository events,
                           EvidenceValidator validator, EvidenceStorage storage, CryptoService crypto,
                           AuditService audit, Clock clock) {
        this.access = access;
        this.evidence = evidence;
        this.events = events;
        this.validator = validator;
        this.storage = storage;
        this.crypto = crypto;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public EvidenceView upload(AuthenticatedAccount reporter, String caseId, MultipartFile file) {
        Complaint complaint = access.forReporter(reporter, caseId);
        if (complaint.getStatus() == ComplaintStatus.CLOSED) {
            throw new ApiException(409, "This case is closed and cannot receive new evidence.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(400, "The file could not be read.");
        }
        EvidenceValidator.Validated validated =
                validator.validate(file.getOriginalFilename(), file.getContentType(), bytes);

        String fileId = UUID.randomUUID().toString();
        String storageKey;
        try {
            storageKey = storage.write(crypto.encryptBytes(bytes, CaseViewMapper.evidenceBytesAad(caseId, fileId)));
        } catch (IOException e) {
            throw new ApiException(500, "The file could not be stored. Please try again.");
        }
        Instant now = clock.instant();
        evidence.save(new EvidenceFile(
                fileId,
                complaint.getId(),
                crypto.encryptText(validated.safeFilename(), CaseViewMapper.filenameAad(caseId, fileId)),
                validated.contentType(),
                bytes.length,
                storageKey,
                now));
        events.save(new ComplaintEvent(complaint.getId(), EventType.EVIDENCE_ADDED, null, null, null, null, now));
        complaint.touch(now);
        audit.record(reporter.accountId(), complaint.getOrganisationId(), caseId, "EVIDENCE_UPLOADED");
        return new EvidenceView(fileId, validated.safeFilename(), validated.contentType(), bytes.length, now);
    }

    @Transactional
    public Download download(AuthenticatedAccount caller, String caseId, String fileId) {
        Complaint complaint = caller.accountType() == AccountType.PERSONAL
                ? access.forReporter(caller, caseId)
                : access.forResponder(caller, caseId);
        EvidenceFile file = evidence.findByPublicIdAndComplaintId(fileId, complaint.getId())
                .orElseThrow(() -> new ApiException(404, "File not found."));
        byte[] plaintext;
        try {
            plaintext = crypto.decryptBytes(storage.read(file.getStorageKey()),
                    CaseViewMapper.evidenceBytesAad(caseId, fileId));
        } catch (IOException e) {
            throw new ApiException(500, "The file could not be read. Please try again.");
        }
        String filename = crypto.decryptText(file.getFilenameCiphertext(), CaseViewMapper.filenameAad(caseId, fileId));
        audit.record(caller.accountId(), complaint.getOrganisationId(), caseId, "EVIDENCE_DOWNLOADED");
        return new Download(filename, file.getContentType(), plaintext);
    }
}
