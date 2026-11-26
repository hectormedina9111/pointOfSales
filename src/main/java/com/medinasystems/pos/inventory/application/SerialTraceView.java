package com.medinasystems.pos.inventory.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.StockReferenceType;
import com.medinasystems.pos.inventory.domain.SerialStatus;

/**
 * A serial unit with its last sale and every movement that moved it, oldest first. The last sale is the one that sold the
 * unit most recently; a unit that came back keeps it.
 */
public record SerialTraceView(long id, long productId, String productSku, String productName, String serialNumber,
                              SerialStatus status, long branchId, String branchName, Instant receivedAt, Instant soldAt,
                              Long saleId, String saleReceiptNumber, String externalCode,
                              Map<String, Object> attributes, List<MovementView> movements) {

    /** One movement of the unit. {@code referenceNumber} is the receipt, or the number of the inventory document. */
    public record MovementView(long id, StockMovementType type, Instant movedAt, String branchName,
                               StockReferenceType referenceType, long referenceId, String referenceNumber) {
    }
}
