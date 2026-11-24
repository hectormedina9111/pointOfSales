package com.medinasystems.pos.sync.application;

import java.time.Clock;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.sync.domain.StoreNode;
import com.medinasystems.pos.sync.domain.StoreTokens;
import com.medinasystems.pos.sync.infrastructure.StoreNodeRepository;

/**
 * Central node: a store exchanges its one-time activation code for its permanent token. The code is consumed
 * in the same transaction that records the activation, so a repeated call with the same code fails.
 */
@Service
@Transactional
public class CentralActivationService {

    private final StoreNodeRepository nodes;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CentralActivationService(StoreNodeRepository nodes, ApplicationEventPublisher events, Clock clock) {
        this.nodes = nodes;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @throws BusinessRuleException INVALID_ACTIVATION_CODE when the code is unknown, used or expired, or
     *                               STORE_REVOKED when the store was revoked
     */
    public ActivationResult activate(String activationCode) {
        String codeHash = StoreTokens.sha256Hex(activationCode);
        StoreNode node = nodes.findByActivationCodeHash(codeHash)
                .orElseThrow(() -> new BusinessRuleException("INVALID_ACTIVATION_CODE",
                        "The activation code is invalid or has expired"));
        Map<String, Object> before = StoreNodeSnapshot.of(node);
        String token = node.activate(codeHash, clock.instant());
        events.publishEvent(AuditEvent.updated("STORE_NODE_ACTIVATED", "store_node", node.getId(), before,
                StoreNodeSnapshot.of(node)));
        return new ActivationResult(node.getId(), node.getNodeNumber(), node.getBranchId(), node.getIdRangeStart(),
                node.getIdRangeEnd(), token);
    }
}
