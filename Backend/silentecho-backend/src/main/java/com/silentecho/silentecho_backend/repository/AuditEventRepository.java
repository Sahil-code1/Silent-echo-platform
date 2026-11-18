package com.silentecho.silentecho_backend.repository;

import com.silentecho.silentecho_backend.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
}
