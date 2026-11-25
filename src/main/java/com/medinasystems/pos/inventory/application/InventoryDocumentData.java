package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.util.List;

import com.medinasystems.pos.inventory.domain.InventoryDocumentType;

/**
 * An inventory document to post. A branch is required on the central node and optional on a store, where it is the store's
 * own branch. Entries and exits read {@code quantity}; adjustments read {@code countedQuantity}.
 */
public record InventoryDocumentData(InventoryDocumentType type, Long branchId, String reason, String notes,
                                    String idempotencyKey, List<LineData> lines) {

    public record LineData(long productId, BigDecimal quantity, BigDecimal countedQuantity, BigDecimal unitCost) {
    }
}
