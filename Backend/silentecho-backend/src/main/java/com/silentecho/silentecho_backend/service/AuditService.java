package com.silentecho.silentecho_backend.service;

import com.silentecho.silentecho_backend.entity.AuditEvent;
import com.silentecho.silentecho_backend.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Writes audit records. Callers pass only identifiers and action names, never content. */
@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void record(String actorAccountId, String organisationId, String caseId, String action) {
        repository.save(new AuditEvent(actorAccountId, organisationId, caseId, action, clock.instant()));
    }
}
