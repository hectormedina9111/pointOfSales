package com.medinasystems.pos.audit.web;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.audit.application.AuditLogService;
import com.medinasystems.pos.audit.application.AuditLogView;
import com.medinasystems.pos.shared.web.PageResponse;

/** Read-only access to the audit log. Times are ISO-8601 instants, for example 2026-11-24T00:00:00Z. */
@RestController
@RequestMapping("/api/v1/audit-log")
public class AuditLogController {

    private final AuditLogService auditLog;

    public AuditLogController(AuditLogService auditLog) {
        this.auditLog = auditLog;
    }

    @GetMapping
    public PageResponse<AuditLogView> search(@RequestParam(required = false) String entity,
                                             @RequestParam(required = false) Long entityId,
                                             @RequestParam(required = false) Instant from,
                                             @RequestParam(required = false) Instant to,
                                             @PageableDefault(sort = {"occurredAt", "id"},
                                                     direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(auditLog.search(entity, entityId, from, to, pageable));
    }
}
