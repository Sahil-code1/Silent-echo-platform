package com.silentecho.silentecho_backend.service;

import com.silentecho.silentecho_backend.dto.CaseDtos.CaseDetail;
import com.silentecho.silentecho_backend.dto.CaseDtos.CaseSummary;
import com.silentecho.silentecho_backend.dto.CaseDtos.CreateComplaintRequest;
import com.silentecho.silentecho_backend.dto.CaseCategories;
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
import java.util.UUID;

/** Use cases for the reporter (personal) workspace. */
@Service
public class ReporterCaseService {

    private final ComplaintRepository complaints;
    private final ComplaintEventRepository events;
    private final ComplaintMessageRepository messages;
    private final OrganisationRepository organisations;
    private final CaseAccess access;
    private final CaseViewMapper mapper;
    private final CryptoService crypto;
    private final AuditService audit;
    private final Clock clock;

    public ReporterCaseService(ComplaintRepository complaints, ComplaintEventRepository events,
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

    @Transactional
    public CaseDetail submit(AuthenticatedAccount reporter, CreateComplaintRequest request) {
        if (reporter.accountType() != AccountType.PERSONAL) {
            throw new ApiException(403, "Only personal accounts can submit a report.");
        }
        if (!CaseCategories.isAllowed(request.category())) {
            throw new ApiException(400, "Choose a category from the list.");
        }
        Organisation organisation = organisations.findById(request.organisationId())
                .orElseThrow(() -> new ApiException(400, "Choose a valid organisation."));
        String caseId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        Complaint complaint = complaints.save(new Complaint(
                caseId,
                reporter.accountId(),
                organisation.getId(),
                request.category().trim(),
                request.urgency(),
                crypto.encryptText(request.title().trim(), CaseViewMapper.titleAad(caseId)),
                crypto.encryptText(request.description().trim(), CaseViewMapper.descriptionAad(caseId)),
                now));
        events.save(new ComplaintEvent(complaint.getId(), EventType.SUBMITTED, null,
                ComplaintStatus.SUBMITTED, request.urgency(), complaint.getCategory(), now));
        audit.record(reporter.accountId(), organisation.getId(), caseId, "CASE_SUBMITTED");
        return mapper.detail(complaint, organisation.getName());
    }

    @Transactional(readOnly = true)
    public List<CaseSummary> listMine(AuthenticatedAccount reporter) {
        if (reporter.accountType() != AccountType.PERSONAL) {
            throw new ApiException(403, "Only personal accounts can view reports.");
        }
        return complaints.findByReporterAccountIdOrderByCreatedAtDesc(reporter.accountId()).stream()
                .map(c -> mapper.summary(c, organisationName(c)))
                .toList();
    }

    @Transactional
    public CaseDetail detail(AuthenticatedAccount reporter, String caseId) {
        Complaint complaint = access.forReporter(reporter, caseId);
        audit.record(reporter.accountId(), complaint.getOrganisationId(), caseId, "CASE_VIEWED");
        return mapper.detail(complaint, organisationName(complaint));
    }

    @Transactional
    public CaseDetail sendMessage(AuthenticatedAccount reporter, String caseId, String body) {
        Complaint complaint = access.forReporter(reporter, caseId);
        Instant now = clock.instant();
        messages.save(new ComplaintMessage(complaint.getId(), MessageSender.REPORTER, null,
                crypto.encryptText(body.trim(), CaseViewMapper.messageAad(caseId)), now));
        complaint.touch(now);
        audit.record(reporter.accountId(), complaint.getOrganisationId(), caseId, "MESSAGE_SENT_BY_REPORTER");
        return mapper.detail(complaint, organisationName(complaint));
    }

    private String organisationName(Complaint complaint) {
        return organisations.findById(complaint.getOrganisationId())
                .map(Organisation::getName)
                .orElse("Organisation");
    }
}
