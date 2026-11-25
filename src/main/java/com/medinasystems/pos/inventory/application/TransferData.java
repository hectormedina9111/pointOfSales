package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * A transfer to send. A branch is required on the central node and optional on a store, where it is the store's own branch.
 * {@code serialNumbers} lists one serial number per unit of a product that tracks them.
 */
public record TransferData(Long originBranchId, long destinationBranchId, String notes, String idempotencyKey,
                           List<LineData> lines) {

    public record LineData(long productId, BigDecimal quantity, List<String> serialNumbers) {
    }
}
