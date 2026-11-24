package com.medinasystems.pos.shared;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A sensitive change to record in the audit log. Modules publish it with Spring's
 * {@code ApplicationEventPublisher}; the audit module stores it in the same transaction as the change.
 *
 * @param action    what happened, such as {@code BRANCH_CREATED}
 * @param entity    table of the changed row, such as {@code branch}
 * @param entityId  id of the changed row
 * @param oldValues previous values of the changed fields, or null when the row is new
 * @param newValues new values of the changed fields
 */
public record AuditEvent(String action, String entity, Long entityId,
                         Map<String, Object> oldValues, Map<String, Object> newValues) {

    public AuditEvent {
        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(entity, "entity is required");
        oldValues = copyOrNull(oldValues);
        newValues = copyOrNull(newValues);
    }

    /** Event for a row that was just created: there are no old values. */
    public static AuditEvent created(String action, String entity, Long entityId, Map<String, Object> newValues) {
        return new AuditEvent(action, entity, entityId, null, newValues);
    }

    /**
     * Event for an update. Only the fields whose value changed are kept, so the log stays small.
     *
     * @param before snapshot of the row before the change
     * @param after  snapshot of the row after the change, with the same keys
     */
    public static AuditEvent updated(String action, String entity, Long entityId,
                                     Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        after.forEach((field, value) -> {
            Object previous = before.get(field);
            if (!Objects.equals(previous, value)) {
                oldValues.put(field, previous);
                newValues.put(field, value);
            }
        });
        return new AuditEvent(action, entity, entityId, oldValues, newValues);
    }

    private static Map<String, Object> copyOrNull(Map<String, Object> values) {
        return values == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
