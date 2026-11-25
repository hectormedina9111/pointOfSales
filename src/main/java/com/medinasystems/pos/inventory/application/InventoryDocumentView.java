package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.medinasystems.pos.inventory.domain.InventoryDocument;
import com.medinasystems.pos.inventory.domain.InventoryDocumentLine;
import com.medinasystems.pos.inventory.domain.InventoryDocumentType;

/** An inventory document with its lines, as the detail screen shows it. */
public record InventoryDocumentView(long id, String number, InventoryDocumentType type, long branchId, String branchName,
                                    String reason, String notes, Instant postedAt, long userId, String userName,
                                    List<LineView> lines) {

    public record LineView(long productId, String productSku, String productName, BigDecimal quantity,
                           BigDecimal countedQuantity, BigDecimal previousQuantity, BigDecimal unitCost) {
    }

    static InventoryDocumentView of(InventoryDocument document, InventoryNames names) {
        List<LineView> lines = document.getLines().stream()
                .map(line -> lineView(line, names))
                .toList();
        return new InventoryDocumentView(document.getId(), document.getNumber(), document.getType(),
                document.getBranchId(), names.branchName(document.getBranchId()), document.getReason(),
                document.getNotes(), document.getPostedAt(), document.getUserId(),
                names.userName(document.getUserId()), lines);
    }

    private static LineView lineView(InventoryDocumentLine line, InventoryNames names) {
        return new LineView(line.getProductId(), names.productSku(line.getProductId()),
                names.productName(line.getProductId()), line.getQuantity(), line.getCountedQuantity(),
                line.getPreviousQuantity(), line.getUnitCost());
    }
}
