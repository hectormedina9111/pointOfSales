package com.medinasystems.pos.inventory.application;

import java.util.List;

import com.medinasystems.pos.inventory.StockMovement;
import com.medinasystems.pos.inventory.domain.InventoryItem;

/**
 * The stock movements of a store that come from sales and returns (event INVENTORY_MOVEMENTS), with the resulting stock of
 * each product they changed and the serial units they moved. The movements refer to a sale or a return document, not to an
 * inventory document. Field names are part of the protocol; the central node reads them in
 * {@link InventoryMovementsUploadHandler}. The movement and item shapes are the same as in {@link InventoryDocumentDocument}.
 *
 * @param createdBy the user who made the sales, as the central node records it in the audit columns
 */
public record InventoryMovementsDocument(long branchId, String createdBy, String updatedBy,
                                         List<InventoryDocumentDocument.MovementDocument> movements,
                                         List<InventoryDocumentDocument.ItemDocument> items,
                                         List<InventoryDocumentDocument.SerialDocument> serials,
                                         List<InventoryDocumentDocument.MovementSerialDocument> movementSerials) {

    public static final String EVENT_TYPE = "INVENTORY_MOVEMENTS";

    public static InventoryMovementsDocument of(long branchId, String by, List<StockMovement> moved,
                                                List<InventoryItem> items, SerialTrail.Trail trail) {
        return new InventoryMovementsDocument(branchId, by, by,
                moved.stream().map(InventoryDocumentDocument.MovementDocument::of).toList(),
                items.stream().map(InventoryDocumentDocument.ItemDocument::of).toList(),
                trail.serials().stream().map(InventoryDocumentDocument.SerialDocument::of).toList(),
                trail.links().stream().map(InventoryDocumentDocument.MovementSerialDocument::of).toList());
    }
}
