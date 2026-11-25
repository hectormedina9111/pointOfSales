package com.medinasystems.pos.sync.application;

import java.time.Clock;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.sync.Outbox;
import com.medinasystems.pos.sync.infrastructure.SyncOutboxRepository;

import tools.jackson.databind.json.JsonMapper;

/** Store side: writes the upload row in the caller's transaction. */
@Component
@Profile("store")
public class StoreOutbox implements Outbox {

    private final SyncOutboxRepository outbox;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public StoreOutbox(SyncOutboxRepository outbox, JsonMapper jsonMapper, Clock clock) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public void enqueue(String eventType, Object payload) {
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(payload, "payload is required");
        outbox.append(eventType, jsonMapper.writeValueAsString(payload), clock.instant());
    }
}
