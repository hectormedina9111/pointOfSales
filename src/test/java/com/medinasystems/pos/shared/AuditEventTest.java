package com.medinasystems.pos.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AuditEventTest {

    @Test
    void updateKeepsOnlyTheFieldsThatChanged() {
        AuditEvent event = AuditEvent.updated("BRANCH_UPDATED", "branch", 7L,
                Map.of("name", "Centro", "phone", "2222-0000"),
                Map.of("name", "Centro Norte", "phone", "2222-0000"));

        assertThat(event.oldValues()).containsExactly(Map.entry("name", "Centro"));
        assertThat(event.newValues()).containsExactly(Map.entry("name", "Centro Norte"));
    }

    @Test
    void createdEventHasNoOldValues() {
        AuditEvent event = AuditEvent.created("COUNTRY_CREATED", "country", 3L, Map.of("isoCode", "HN"));

        assertThat(event.oldValues()).isNull();
        assertThat(event.newValues()).containsEntry("isoCode", "HN");
    }
}
