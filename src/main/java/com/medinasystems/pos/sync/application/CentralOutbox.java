package com.medinasystems.pos.sync.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.sync.Outbox;

/**
 * Central side: the bean exists so producers can be injected on both profiles, but only a store queues uploads. A call
 * here is a bug in the producer, so it fails instead of losing the document silently.
 */
@Component
@Profile("central")
public class CentralOutbox implements Outbox {

    @Override
    public void enqueue(String eventType, Object payload) {
        throw new IllegalStateException("Only a store node queues uploads; the central node never enqueues " + eventType);
    }
}
