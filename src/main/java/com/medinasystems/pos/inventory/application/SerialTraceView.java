package com.medinasystems.pos.inventory.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.StockReferenceType;

/**
 * One entry of the trace of a serial number. {@code source} is EQUIPMENT for a unit of a product with serial mode LIST: it
 * has its id, its status (AVAILABLE, SOLD, IN_TRANSIT or REMOVED), its characteristics and its movements, oldest first. It
 * is SALE for a unit of a product with serial mode MANUAL, which only the sale knows: its status is SOLD, or RETURNED once
 * it came back, and it has no id, characteristics or movements. The last sale is the one that sold the unit most recently;
 * a unit that came back keeps it.
 */
public record SerialTraceView(Long id, long productId, String productSku, String productName, String serialNumber,
                              String status, Long branchId, String branchName, Instant receivedAt, Instant soldAt,
                              Long saleId, String saleReceiptNumber, String externalCode,
                              Map<String, Object> attributes, List<MovementView> movements, String source) {

    /** Source of an entry that is a unit of the inventory (serial mode LIST). */
    public static final String SOURCE_EQUIPMENT = "EQUIPMENT";
    /** Source of an entry that is a sale of a MANUAL serial number. */
    public static final String SOURCE_SALE = "SALE";

    /** One movement of the unit. {@code referenceNumber} is the receipt, or the number of the inventory document. */
    public record MovementView(long id, StockMovementType type, Instant movedAt, String branchName,
                               StockReferenceType referenceType, long referenceId, String referenceNumber) {
    }
}
