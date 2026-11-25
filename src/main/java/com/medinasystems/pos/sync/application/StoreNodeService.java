package com.medinasystems.pos.sync.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;
import com.medinasystems.pos.sync.StoreNodeMetrics;
import com.medinasystems.pos.sync.domain.StoreNode;
import com.medinasystems.pos.sync.infrastructure.StoreNodeRepository;
import com.medinasystems.pos.sync.infrastructure.SyncDataReader;

/** Central node: registers stores, issues their activation codes and revokes them. */
@Service
@Transactional
public class StoreNodeService {

    private final StoreNodeRepository nodes;
    private final BranchDirectory branches;
    private final SyncDataReader changeLog;
    private final StoreNodeMetrics metrics;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public StoreNodeService(StoreNodeRepository nodes, BranchDirectory branches, SyncDataReader changeLog,
                            StoreNodeMetrics metrics, ApplicationEventPublisher events, Clock clock) {
        this.nodes = nodes;
        this.branches = branches;
        this.changeLog = changeLog;
        this.metrics = metrics;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<StoreNodeView> list(Pageable pageable) {
        long latest = changeLog.latestVersion();
        Instant now = clock.instant();
        return nodes.findAll(pageable).map(node -> StoreNodeView.from(node, latest, now,
                metrics.returnConflicts(node.getId())));
    }

    @Transactional(readOnly = true)
    public StoreNodeView get(long id) {
        return view(find(id));
    }

    /** Registers a store with the next free node number. The branch is optional, and each branch has one store. */
    public StoreNodeView create(Long branchId) {
        if (branchId != null) {
            if (!branches.exists(branchId)) {
                throw new BusinessRuleException("BRANCH_NOT_FOUND", "One or more branch ids do not exist");
            }
            if (nodes.existsByBranchId(branchId)) {
                throw new BusinessRuleException("DUPLICATE_STORE_BRANCH", "The branch already has a store node");
            }
        }
        int nodeNumber = nodes.findTopByOrderByNodeNumberDesc()
                .map(last -> last.getNodeNumber() + 1)
                .orElse(1);
        if (nodeNumber > StoreNode.MAX_NODE_NUMBER) {
            throw new BusinessRuleException("STORE_NODE_LIMIT_REACHED", "No store node numbers are left");
        }
        StoreNode saved = nodes.save(new StoreNode(nodeNumber, branchId));
        events.publishEvent(AuditEvent.created("STORE_NODE_CREATED", "store_node", saved.getId(),
                StoreNodeSnapshot.of(saved)));
        return view(saved);
    }

    /** Issues a new activation code for a pending or active store. The code is returned only here. */
    public ActivationCodeView generateActivationCode(long id) {
        StoreNode node = find(id);
        String code = node.generateActivationCode(clock);
        events.publishEvent(AuditEvent.created("STORE_NODE_ACTIVATION_CODE_CREATED", "store_node", node.getId(),
                Map.of("activationExpiresAt", node.getActivationExpiresAt().toString())));
        return new ActivationCodeView(code, node.getActivationExpiresAt());
    }

    /** Revokes a store: its token stops working at once and its branch is released. */
    public StoreNodeView revoke(long id) {
        StoreNode node = find(id);
        Map<String, Object> before = StoreNodeSnapshot.of(node);
        node.revoke(clock.instant());
        events.publishEvent(AuditEvent.updated("STORE_NODE_REVOKED", "store_node", node.getId(), before,
                StoreNodeSnapshot.of(node)));
        return view(node);
    }

    /**
     * Assigns the branch a store serves, or clears it with null. A branch belongs to one store at most. The store
     * receives the change on its next heartbeat.
     */
    public StoreNodeView assignBranch(long id, Long branchId) {
        StoreNode node = find(id);
        if (branchId != null) {
            if (!branches.exists(branchId)) {
                throw new BusinessRuleException("BRANCH_NOT_FOUND", "One or more branch ids do not exist");
            }
            if (nodes.existsByBranchIdAndIdNot(branchId, id)) {
                throw new BusinessRuleException("DUPLICATE_STORE_BRANCH", "The branch already has a store node");
            }
        }
        Long previous = node.getBranchId();
        Map<String, Object> before = StoreNodeSnapshot.of(node);
        node.assignBranch(branchId);
        if (!Objects.equals(previous, branchId)) {
            events.publishEvent(AuditEvent.updated("STORE_NODE_BRANCH_CHANGED", "store_node", node.getId(), before,
                    StoreNodeSnapshot.of(node)));
        }
        return view(node);
    }

    private StoreNodeView view(StoreNode node) {
        return StoreNodeView.from(node, changeLog.latestVersion(), clock.instant(),
                metrics.returnConflicts(node.getId()));
    }

    private StoreNode find(long id) {
        return nodes.findById(id).orElseThrow(() -> new ResourceNotFoundException("store node", id));
    }
}
