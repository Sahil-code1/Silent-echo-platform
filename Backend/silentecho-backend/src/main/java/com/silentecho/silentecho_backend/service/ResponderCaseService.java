package com.silentecho.silentecho_backend.service;

import com.silentecho.silentecho_backend.dto.CaseCategories;
import com.silentecho.silentecho_backend.dto.CaseDtos.CaseDetail;
import com.silentecho.silentecho_backend.dto.CaseDtos.CaseSummary;
import com.silentecho.silentecho_backend.dto.CaseDtos.UpdateCaseRequest;
import com.silentecho.silentecho_backend.entity.*;
import com.silentecho.silentecho_backend.exception.ApiException;
import com.silentecho.silentecho_backend.repository.ComplaintEventRepository;
import com.silentecho.silentecho_backend.repository.ComplaintMessageRepository;
import com.silentecho.silentecho_backend.repository.ComplaintRepository;
import com.silentecho.silentecho_backend.repository.OrganisationRepository;
import com.silentecho.silentecho_backend.security.AuthenticatedAccount;
import com.silentecho.silentecho_backend.security.CaseAccess;
import com.silentecho.silentecho_backend.security.CryptoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Use cases for the organisation responder workspace. Every method is scoped to the caller's organisations. */
@Service
public class ResponderCaseService {

    private final ComplaintRepository complaints;
    private final ComplaintEventRepository events;
    private final ComplaintMessageRepository messages;
    private final OrganisationRepository organisations;
    private final CaseAccess access;
    private final CaseViewMapper mapper;
    private final CryptoService crypto;
    private final AuditService audit;
    private final Clock clock;

    public ResponderCaseService(ComplaintRepository complaints, ComplaintEventRepository events,
                                ComplaintMessageRepository messages, OrganisationRepository organisations,
                                CaseAccess access, CaseViewMapper mapper, CryptoService crypto,
                                AuditService audit, Clock clock) {
        this.complaints = complaints;
        this.events = events;
        this.messages = messages;
        this.organisations = organisations;
        this.access = access;
        this.mapper = mapper;
        this.crypto = crypto;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CaseSummary> inbox(AuthenticatedAccount responder) {
        List<String> organisationIds = access.organisationIds(responder);
        if (responder.accountType() != AccountType.ORGANISATION || organisationIds.isEmpty()) {
            return List.of();
        }
        return complaints.findByOrganisationIdInOrderByCreatedAtDesc(organisationIds).stream()
                .map(c -> mapper.summary(c, organisationName(c)))
                .toList();
    }

    @Transactional
    public CaseDetail detail(AuthenticatedAccount responder, String caseId) {
        Complaint complaint = access.forResponder(responder, caseId);
        audit.record(responder.accountId(), complaint.getOrganisationId(), caseId, "CASE_VIEWED");
        return mapper.detail(complaint, organisationName(complaint));
    }

    /** Applies only the fields supplied. Each change is written to the case timeline. */
    @Transactional
    public CaseDetail update(AuthenticatedAccount responder, String caseId, UpdateCaseRequest request) {
        Complaint complaint = access.forResponder(responder, caseId);
        access.requireRole(responder, complaint.getOrganisationId(), MembershipRole.ADMIN, MembershipRole.RESPONDER);

        boolean hasNote = request.actionNote() != null && !request.actionNote().isBlank();
        boolean changedStatus = request.status() != null && request.status() != complaint.getStatus();
        boolean changedPriority = request.urgency() != null && request.urgency() != complaint.getUrgency();
        boolean changedCategory = request.category() != null && !request.category().isBlank()
                && !request.category().trim().equals(complaint.getCategory());
        if (!changedStatus && !changedPriority && !changedCategory && !hasNote) {
            throw new ApiException(400, "Nothing to update.");
        }
        if (changedCategory && !CaseCategories.isAllowed(request.category().trim())) {
            throw new ApiException(400, "Choose a category from the list.");
        }

        Instant now = clock.instant();
        if (changedStatus) {
            complaint.setStatus(request.status());
            events.save(new ComplaintEvent(complaint.getId(), EventType.STATUS_CHANGED, null,
                    complaint.getStatus(), null, null, now));
        }
        if (changedPriority) {
            complaint.setUrgency(request.urgency());
            events.save(new ComplaintEvent(complaint.getId(), EventType.PRIORITY_CHANGED, null,
                    null, complaint.getUrgency(), null, now));
        }
        if (changedCategory) {
            complaint.setCategory(request.category().trim());
            events.save(new ComplaintEvent(complaint.getId(), EventType.CATEGORY_CHANGED, null,
                    null, null, complaint.getCategory(), now));
        }
        if (hasNote) {
            events.save(new ComplaintEvent(complaint.getId(), EventType.ACTION_NOTE,
                    crypto.encryptText(request.actionNote().trim(), CaseViewMapper.noteAad(caseId)),
                    complaint.getStatus(), complaint.getUrgency(), complaint.getCategory(), now));
        }
        complaint.touch(now);
        audit.record(responder.accountId(), complaint.getOrganisationId(), caseId, "CASE_UPDATED");
        return mapper.detail(complaint, organisationName(complaint));
    }

    @Transactional
    public CaseDetail sendMessage(AuthenticatedAccount responder, String caseId, String body) {
        Complaint complaint = access.forResponder(responder, caseId);
        access.requireRole(responder, complaint.getOrganisationId(), MembershipRole.ADMIN, MembershipRole.RESPONDER);
        Instant now = clock.instant();
        // The author ID is kept for internal audit. It is never returned to the reporter.
        messages.save(new ComplaintMessage(complaint.getId(), MessageSender.ORGANISATION, responder.accountId(),
                crypto.encryptText(body.trim(), CaseViewMapper.messageAad(caseId)), now));
        complaint.touch(now);
        audit.record(responder.accountId(), complaint.getOrganisationId(), caseId, "MESSAGE_SENT_BY_RESPONDER");
        return mapper.detail(complaint, organisationName(complaint));
    }

    private String organisationName(Complaint complaint) {
        return organisations.findById(complaint.getOrganisationId())
                .map(Organisation::getName)
                .orElse("Organisation");
    }
}
