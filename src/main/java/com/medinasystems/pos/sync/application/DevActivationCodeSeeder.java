package com.medinasystems.pos.sync.application;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.sync.domain.StoreNode;
import com.medinasystems.pos.sync.domain.StoreTokens;
import com.medinasystems.pos.sync.infrastructure.StoreNodeRepository;

/**
 * LOCAL DEVELOPMENT ONLY. Central node, at every startup: creates the store nodes listed in
 * {@code pos.sync.dev-activation-codes} and sets their known activation codes, so the local stores
 * activate without a manual step. Codes are renewed at each startup and never logged. Stores already
 * activated are left alone. In production the codes come from the admin screen (POST /store-nodes/{id}/activation-code).
 */
@Component
@Profile("central")
public class DevActivationCodeSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevActivationCodeSeeder.class);

    private final SyncProperties sync;
    private final StoreNodeRepository nodes;
    private final Clock clock;

    public DevActivationCodeSeeder(SyncProperties sync, StoreNodeRepository nodes, Clock clock) {
        this.sync = sync;
        this.nodes = nodes;
        this.clock = clock;
    }

    /** Transactional on purpose: the entities changed here must be saved in the same transaction. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String configured = sync.devActivationCodes();
        if (configured == null || configured.isBlank()) {
            return;
        }
        for (String entry : configured.split(",")) {
            if (entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split(":", 2);
            if (parts.length != 2 || parts[1].isBlank()) {
                throw new IllegalStateException("Invalid entry in pos.sync.dev-activation-codes; expected nodeNumber:code");
            }
            seed(Integer.parseInt(parts[0].trim()), parts[1].trim());
        }
    }

    void seed(int nodeNumber, String code) {
        StoreNode node = nodes.findByNodeNumber((short) nodeNumber)
                .orElseGet(() -> nodes.save(new StoreNode(nodeNumber, null)));
        if (node.getActivatedAt() != null || node.isRevoked()) {
            log.info("dev activation code skipped for store node {}: already activated or revoked", nodeNumber);
            return;
        }
        node.prepareActivation(StoreTokens.sha256Hex(code), clock.instant());
        log.info("dev activation code prepared for store node {}", nodeNumber);
    }
}
