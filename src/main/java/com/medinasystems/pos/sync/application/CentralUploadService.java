package com.medinasystems.pos.sync.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.medinasystems.pos.shared.ResourceNotFoundException;
import com.medinasystems.pos.sync.UploadHandler;
import com.medinasystems.pos.sync.UploadOutcome;
import com.medinasystems.pos.sync.application.SyncProtocol.UploadItem;
import com.medinasystems.pos.sync.application.SyncProtocol.UploadResponse;
import com.medinasystems.pos.sync.application.SyncProtocol.UploadResult;
import com.medinasystems.pos.sync.domain.StoreNode;
import com.medinasystems.pos.sync.infrastructure.StoreNodeRepository;
import com.medinasystems.pos.sync.infrastructure.SyncUploadReceiptRepository;

/**
 * Central node: receives the outbox rows of a store and dispatches each one to the {@link UploadHandler} of its event
 * type. Each item runs in its own transaction together with its receipt, so an item is applied once even when the store
 * sends it again. ACCEPTED and REJECTED are final and receipted; RETRY is not receipted, so the store sends the item
 * again. The central node does not revalidate the business rules. An unexpected error fails the whole request: the
 * store keeps the rows pending and retries; the items already answered come back from their receipts.
 */
@Service
@Profile("central")
public class CentralUploadService {

    private static final String RETRY = "RETRY";

    private final TransactionTemplate transactions;
    private final StoreNodeRepository nodes;
    private final SyncUploadReceiptRepository receipts;
    private final Map<String, UploadHandler> handlers;
    private final Clock clock;

    public CentralUploadService(TransactionTemplate transactions, StoreNodeRepository nodes,
                                SyncUploadReceiptRepository receipts, List<UploadHandler> handlers, Clock clock) {
        this.transactions = transactions;
        this.nodes = nodes;
        this.receipts = receipts;
        this.handlers = byEventType(handlers);
        this.clock = clock;
    }

    /**
     * @throws ResourceNotFoundException when the store node does not exist
     */
    public UploadResponse upload(long storeNodeId, List<UploadItem> items) {
        StoreNode node = nodes.findById(storeNodeId)
                .orElseThrow(() -> new ResourceNotFoundException("store node", storeNodeId));
        Long branchId = node.getBranchId();
        List<UploadResult> results = new ArrayList<>(items.size());
        for (UploadItem item : items) {
            results.add(transactions.execute(status -> processItem(storeNodeId, branchId, item)));
        }
        return new UploadResponse(results);
    }

    private UploadResult processItem(long storeNodeId, Long branchId, UploadItem item) {
        Optional<String> stored = receipts.findResult(storeNodeId, item.outboxId());
        if (stored.isPresent()) {
            return new UploadResult(item.outboxId(), stored.get(), null);
        }
        UploadOutcome outcome = apply(storeNodeId, branchId, item);
        if (outcome.status() == UploadOutcome.Status.RETRY) {
            return new UploadResult(item.outboxId(), RETRY, outcome.code());
        }
        receipts.save(storeNodeId, item.outboxId(), clock.instant(), outcome.status().name());
        return new UploadResult(item.outboxId(), outcome.status().name(), outcome.code());
    }

    private UploadOutcome apply(long storeNodeId, Long branchId, UploadItem item) {
        UploadHandler handler = handlers.get(item.eventType());
        if (handler == null) {
            return UploadOutcome.rejected("UNKNOWN_EVENT");
        }
        return handler.apply(storeNodeId, branchId, item.payload());
    }

    private static Map<String, UploadHandler> byEventType(List<UploadHandler> handlers) {
        Map<String, UploadHandler> byType = new HashMap<>();
        for (UploadHandler handler : handlers) {
            if (byType.putIfAbsent(handler.eventType(), handler) != null) {
                throw new IllegalStateException("Two upload handlers for the event " + handler.eventType());
            }
        }
        return Map.copyOf(byType);
    }
}
