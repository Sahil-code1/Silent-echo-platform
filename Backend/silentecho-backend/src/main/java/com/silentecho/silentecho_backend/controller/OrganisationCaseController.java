package com.silentecho.silentecho_backend.controller;

import com.silentecho.silentecho_backend.dto.CaseDtos.*;
import com.silentecho.silentecho_backend.security.CurrentAccount;
import com.silentecho.silentecho_backend.service.EvidenceService;
import com.silentecho.silentecho_backend.service.ResponderCaseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Organisation workspace: inbox, case management, responder messages and evidence download. */
@RestController
@RequestMapping("/api/org/cases")
public class OrganisationCaseController {

    private final ResponderCaseService cases;
    private final EvidenceService evidence;

    public OrganisationCaseController(ResponderCaseService cases, EvidenceService evidence) {
        this.cases = cases;
        this.evidence = evidence;
    }

    @GetMapping
    public List<CaseSummary> inbox() {
        return cases.inbox(CurrentAccount.require());
    }

    @GetMapping("/{caseId}")
    public CaseDetail detail(@PathVariable String caseId) {
        return cases.detail(CurrentAccount.require(), caseId);
    }

    @PatchMapping("/{caseId}")
    public CaseDetail update(@PathVariable String caseId, @Valid @RequestBody UpdateCaseRequest body) {
        return cases.update(CurrentAccount.require(), caseId, body);
    }

    @PostMapping("/{caseId}/messages")
    public CaseDetail sendMessage(@PathVariable String caseId, @Valid @RequestBody SendMessageRequest body) {
        return cases.sendMessage(CurrentAccount.require(), caseId, body.body());
    }

    @GetMapping("/{caseId}/evidence/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable String caseId, @PathVariable String fileId) {
        return EvidenceResponses.attachment(evidence.download(CurrentAccount.require(), caseId, fileId));
    }
}
