package com.medinasystems.pos.sync.application;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.medinasystems.pos.security.RemotePasswordChanges;
import com.medinasystems.pos.shared.ResourceNotFoundException;
import com.medinasystems.pos.sync.application.SyncProtocol.UploadItem;
import com.medinasystems.pos.sync.application.SyncProtocol.UploadResponse;
import com.medinasystems.pos.sync.application.SyncProtocol.UploadResult;
import com.medinasystems.pos.sync.domain.StoreNode;
import com.medinasystems.pos.sync.infrastructure.StoreNodeRepository;
import com.medinasystems.pos.sync.infrastructure.SyncUploadReceiptRepository;

import tools.jackson.databind.JsonNode;

/**
 * Central node: receives the outbox rows of a store. Each item runs in its own transaction together with its
 * receipt, so an item is applied once even when the store sends it again. The central node does not revalidate
 * the business rules; it only applies the event and answers. An unexpected error fails the whole request, the
 * store keeps the rows pending and retries; the items already answered are returned from their receipts.
 */
@Service
@Profile("central")
public class CentralUploadService {

    static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    /** Column size of app_user.password_hash. */
    static final int MAX_PASSWORD_HASH = 255;
    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";

    private final TransactionTemplate transactions;
    private final StoreNodeRepository nodes;
    private final SyncUploadReceiptRepository receipts;
    private final RemotePasswordChanges passwords;
    private final Clock clock;

    public CentralUploadService(TransactionTemplate transactions, StoreNodeRepository nodes,
                                SyncUploadReceiptRepository receipts, RemotePasswordChanges passwords, Clock clock) {
        this.transactions = transactions;
        this.nodes = nodes;
        this.receipts = receipts;
        this.passwords = passwords;
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
        UploadResult result = apply(branchId, item);
        receipts.save(storeNodeId, item.outboxId(), clock.instant(), result.result());
        return result;
    }

    private UploadResult apply(Long branchId, UploadItem item) {
        if (!PASSWORD_CHANGED.equals(item.eventType())) {
            return rejected(item, "UNKNOWN_EVENT");
        }
        PasswordChange change = PasswordChange.from(item.payload());
        if (change == null) {
            return rejected(item, "INVALID_PAYLOAD");
        }
        // A store without a branch serves no user, so it must not change any password.
        if (branchId == null) {
            return rejected(item, "BRANCH_NOT_ALLOWED");
        }
        return switch (passwords.apply(change.userId(), change.passwordHash(), change.changedAt(), branchId)) {
            case APPLIED -> accepted(item, null);
            case STALE -> accepted(item, "STALE");
            case USER_NOT_FOUND -> rejected(item, "USER_NOT_FOUND");
            case BRANCH_NOT_ALLOWED -> rejected(item, "BRANCH_NOT_ALLOWED");
        };
    }

    private static UploadResult accepted(UploadItem item, String code) {
        return new UploadResult(item.outboxId(), ACCEPTED, code);
    }

    private static UploadResult rejected(UploadItem item, String code) {
        return new UploadResult(item.outboxId(), REJECTED, code);
    }

    /** The fields of a PASSWORD_CHANGED payload. Built only when every field is present and well formed. */
    private record PasswordChange(long userId, String passwordHash, Instant changedAt) {

        static PasswordChange from(JsonNode payload) {
            JsonNode userId = payload.path("userId");
            JsonNode hash = payload.path("passwordHash");
            JsonNode changedAt = payload.path("changedAt");
            if (!userId.isIntegralNumber() || !hash.isTextual() || !changedAt.isTextual()) {
                return null;
            }
            String hashText = hash.textValue();
            if (hashText.isBlank() || hashText.length() > MAX_PASSWORD_HASH) {
                return null;
            }
            try {
                return new PasswordChange(userId.longValue(), hashText, Instant.parse(changedAt.textValue()));
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }
}
