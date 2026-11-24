package com.medinasystems.pos.sync.application;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.sync.infrastructure.CentralActivationClient;

/**
 * Store side, at startup. An activated store only adjusts its sequences. A store that is not activated yet
 * activates itself in the background, so startup is not blocked while the central node starts. The code is
 * never logged.
 */
@Component
@Profile("store")
public class StoreActivator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StoreActivator.class);
    static final int MAX_ATTEMPTS = 30;
    static final Duration RETRY_DELAY = Duration.ofSeconds(10);

    private final StoreActivationService activation;
    private final CentralActivationClient client;
    private final SyncProperties sync;
    private final NodeProperties node;

    public StoreActivator(StoreActivationService activation, CentralActivationClient client, SyncProperties sync,
                          NodeProperties node) {
        this.activation = activation;
        this.client = client;
        this.sync = sync;
        this.node = node;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (activation.isActivated()) {
            activation.adjustSequences();
            log.info("Store node {} already activated; id sequences adjusted", node.number());
            return;
        }
        String code = sync.activationCode();
        if (code == null || code.isBlank()) {
            log.warn("Store node {} is not activated: set POS_ACTIVATION_CODE to activate it", node.number());
            return;
        }
        Thread.ofVirtual().name("store-activation").start(() -> activateWithRetries(code));
    }

    void activateWithRetries(String code) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ActivationResult result = client.activate(code);
                if (result.nodeNumber() != node.number()) {
                    log.error("Central node assigned store node {} but this server is configured as {}; "
                            + "identity not saved", result.nodeNumber(), node.number());
                    return;
                }
                activation.saveActivation(result, node.centralUrl());
                log.info("Store node {} activated; ids {} to {}", result.nodeNumber(), result.idRangeStart(),
                        result.idRangeEnd());
                return;
            } catch (HttpClientErrorException ex) {
                // The central node refused the code (unknown, used, expired or revoked): retrying cannot help.
                log.error("Activation refused by the central node with HTTP {}. Check POS_ACTIVATION_CODE "
                        + "and restart the store.", ex.getStatusCode().value());
                return;
            } catch (RestClientException ex) {
                log.warn("Central node not reachable (attempt {} of {}): {}", attempt, MAX_ATTEMPTS, ex.getMessage());
            }
            if (!pause()) {
                return;
            }
        }
        log.error("Store activation gave up after {} attempts; restart the store to try again", MAX_ATTEMPTS);
    }

    /** Returns false when the thread was interrupted. */
    private static boolean pause() {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
