package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * The receipt of a transfer, given by the destination branch: for each line, the quantity received and the serial numbers
 * among the ones sent. A line that is not in the receipt is an error: every line is answered.
 */
public record ReceiptData(String notes, String idempotencyKey, List<LineData> lines) {

    public record LineData(long lineId, BigDecimal receivedQuantity, List<String> serialNumbers) {
    }
}
