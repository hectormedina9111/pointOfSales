package com.medinasystems.pos.audit.application;

import java.time.Instant;
import java.util.Map;

import com.medinasystems.pos.audit.domain.AuditLogEntry;

public record AuditLogView(Long id, Instant occurredAt, Long userId, String username, String action, String entity,
                           Long entityId, Map<String, Object> oldValues, Map<String, Object> newValues,
                           String ipAddress) {

    public static AuditLogView from(AuditLogEntry entry) {
        return new AuditLogView(entry.getId(), entry.getOccurredAt(), entry.getUserId(), entry.getUsername(),
                entry.getAction(), entry.getEntity(), entry.getEntityId(), entry.getOldValues(),
                entry.getNewValues(), entry.getIpAddress());
    }
}
