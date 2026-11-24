package com.medinasystems.pos.audit.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.medinasystems.pos.audit.domain.AuditLogEntry;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long>,
        JpaSpecificationExecutor<AuditLogEntry> {
}
