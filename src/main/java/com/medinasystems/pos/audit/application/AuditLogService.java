package com.medinasystems.pos.audit.application;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.audit.domain.AuditLogEntry;
import com.medinasystems.pos.audit.infrastructure.AuditLogRepository;

/** Queries the audit log. Each filter is optional. */
@Service
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository log;

    public AuditLogService(AuditLogRepository log) {
        this.log = log;
    }

    public Page<AuditLogView> search(String entity, Long entityId, Instant from, Instant to, Pageable pageable) {
        Specification<AuditLogEntry> spec = Specification.unrestricted();
        if (entity != null && !entity.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.<String>get("entity"), entity));
        }
        if (entityId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.<Long>get("entityId"), entityId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.<Instant>get("occurredAt"), to));
        }
        return log.findAll(spec, pageable).map(AuditLogView::from);
    }
}
