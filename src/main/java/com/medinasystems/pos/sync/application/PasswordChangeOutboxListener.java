package com.medinasystems.pos.sync.application;

import java.time.Clock;

import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.security.PasswordChangedEvent;
import com.medinasystems.pos.sync.infrastructure.SyncOutboxRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Store side only: queues a password changed on this store, in the same transaction as the change, so the change
 * is never lost and never sent without being saved. The payload carries the hash, never the clear password.
 */
@Component
@Profile("store")
public class PasswordChangeOutboxListener {

    static final String EVENT_TYPE = "PASSWORD_CHANGED";

    private final SyncOutboxRepository outbox;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public PasswordChangeOutboxListener(SyncOutboxRepository outbox, JsonMapper jsonMapper, Clock clock) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @EventListener
    public void onPasswordChanged(PasswordChangedEvent event) {
        String payload = jsonMapper.writeValueAsString(new PasswordChangePayload(event.userId(),
                event.passwordHash(), event.changedAt().toString()));
        outbox.append(EVENT_TYPE, payload, clock.instant());
    }

    /** The JSON shape the central node reads. Field names are part of the protocol. */
    public record PasswordChangePayload(long userId, String passwordHash, String changedAt) {
    }
}
