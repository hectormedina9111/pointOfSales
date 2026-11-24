package com.medinasystems.pos.audit.domain;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row of the audit log. Append-only: rows are written once and never updated or deleted,
 * so this entity does not extend {@code AuditableEntity}.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "audit_log_entity_idx", columnList = "entity, entity_id"),
        @Index(name = "audit_log_occurred_at_idx", columnList = "occurred_at")})
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 50)
    private String username;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(nullable = false, length = 40)
    private String entity;

    @Column(name = "entity_id")
    private Long entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values")
    private Map<String, Object> oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values")
    private Map<String, Object> newValues;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    protected AuditLogEntry() {
    }

    public AuditLogEntry(Instant occurredAt, Long userId, String username, String action, String entity,
                         Long entityId, Map<String, Object> oldValues, Map<String, Object> newValues,
                         String ipAddress) {
        this.occurredAt = occurredAt;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.oldValues = oldValues;
        this.newValues = newValues;
        this.ipAddress = ipAddress;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getAction() {
        return action;
    }

    public String getEntity() {
        return entity;
    }

    public Long getEntityId() {
        return entityId;
    }

    public Map<String, Object> getOldValues() {
        return oldValues;
    }

    public Map<String, Object> getNewValues() {
        return newValues;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
