package com.silentecho.silentecho_backend.controller;

import com.silentecho.silentecho_backend.dto.CaseDtos.*;
import com.silentecho.silentecho_backend.security.CurrentAccount;
import com.silentecho.silentecho_backend.service.EvidenceService;
import com.silentecho.silentecho_backend.service.ReporterCaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Reporter workspace: submit, track, message and upload evidence. */
@RestController
@RequestMapping("/api/personal/cases")
public class PersonalCaseController {

    private final ReporterCaseService cases;
    private final EvidenceService evidence;

    public PersonalCaseController(ReporterCaseService cases, EvidenceService evidence) {
        this.cases = cases;
        this.evidence = evidence;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CaseDetail submit(@Valid @RequestBody CreateComplaintRequest body) {
        return cases.submit(CurrentAccount.require(), body);
    }

    @GetMapping
    public List<CaseSummary> list() {
        return cases.listMine(CurrentAccount.require());
    }

    @GetMapping("/{caseId}")
    public CaseDetail detail(@PathVariable String caseId) {
        return cases.detail(CurrentAccount.require(), caseId);
    }

    @PostMapping("/{caseId}/messages")
    public CaseDetail sendMessage(@PathVariable String caseId, @Valid @RequestBody SendMessageRequest body) {
        return cases.sendMessage(CurrentAccount.require(), caseId, body.body());
    }

    @PostMapping(value = "/{caseId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EvidenceView upload(@PathVariable String caseId, @RequestParam("file") MultipartFile file) {
        return evidence.upload(CurrentAccount.require(), caseId, file);
    }

    @GetMapping("/{caseId}/evidence/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable String caseId, @PathVariable String fileId) {
        return EvidenceResponses.attachment(evidence.download(CurrentAccount.require(), caseId, fileId));
    }
}
