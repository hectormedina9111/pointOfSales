package com.medinasystems.pos.inventory.application;

import static com.medinasystems.pos.sync.UploadPayload.longField;
import static com.medinasystems.pos.sync.UploadPayload.text;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.inventory.StockReferenceType;
import com.medinasystems.pos.inventory.application.InventoryUploadWriter.Item;
import com.medinasystems.pos.inventory.application.InventoryUploadWriter.Movement;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.sync.StoreIdRanges;
import com.medinasystems.pos.sync.UploadHandler;
import com.medinasystems.pos.sync.UploadOutcome;
import com.medinasystems.pos.sync.UploadPayload.InvalidPayloadException;

import tools.jackson.databind.JsonNode;

/**
 * Central side: stores the sales and return movements that a store uploads (INVENTORY_MOVEMENTS), with the resulting stock
 * of the products they changed. The checks are those of the document handler: completeness, the branch, the id range of
 * the store, and the existence of the branch, the users and the products. The central node does not revalidate the stock.
 * A store sends the batch again when it has not seen the answer, so a batch whose first movement is stored is accepted
 * without writing again. A missing branch, user or product gets RETRY and nothing is written.
 */
@Component
@Profile("central")
public class InventoryMovementsUploadHandler implements UploadHandler {

    private final StoreIdRanges ranges;
    private final BranchDirectory branches;
    private final UserDirectory users;
    private final SaleableProducts products;
    private final InventoryUploadWriter writer;
    private final Clock clock;

    public InventoryMovementsUploadHandler(StoreIdRanges ranges, BranchDirectory branches, UserDirectory users,
                                           SaleableProducts products, InventoryUploadWriter writer, Clock clock) {
        this.ranges = ranges;
        this.branches = branches;
        this.users = users;
        this.products = products;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public String eventType() {
        return InventoryMovementsDocument.EVENT_TYPE;
    }

    @Override
    public UploadOutcome apply(long storeNodeId, Long storeBranchId, JsonNode payload) {
        Upload upload;
        try {
            upload = Upload.from(payload);
        } catch (InvalidPayloadException ex) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        if (storeBranchId == null || storeBranchId != upload.branchId()) {
            return UploadOutcome.rejected("BRANCH_MISMATCH");
        }
        for (Movement movement : upload.movements()) {
            if (!ranges.contains(storeNodeId, movement.id())) {
                return UploadOutcome.rejected("ID_OUT_OF_RANGE");
            }
            // Sales and returns only: a document movement would need its inventory document.
            if (movement.referenceType() != StockReferenceType.SALE
                    && movement.referenceType() != StockReferenceType.SALE_RETURN) {
                return UploadOutcome.rejected("INVALID_PAYLOAD");
            }
        }
        if (writer.exists("SELECT COUNT(*) FROM inventory_movement WHERE id = ?", upload.movements().get(0).id())) {
            return UploadOutcome.accepted();
        }
        if (!branches.exists(upload.branchId())) {
            return UploadOutcome.retry("BRANCH_MISSING");
        }
        for (Movement movement : upload.movements()) {
            if (users.find(movement.userId()).isEmpty()) {
                return UploadOutcome.retry("USER_MISSING");
            }
        }
        for (long productId : upload.productIds()) {
            if (products.find(productId).isEmpty()) {
                return UploadOutcome.retry("PRODUCT_MISSING");
            }
        }
        writer.writeMovements(upload.branchId(), upload.movements(), upload.createdBy(), upload.updatedBy(),
                clock.instant());
        writer.writeItems(upload.branchId(), upload.items(), upload.createdBy(), upload.updatedBy(), clock.instant());
        return UploadOutcome.accepted();
    }

    /** Every field of an INVENTORY_MOVEMENTS payload, read and checked before anything is written. */
    private record Upload(long branchId, String createdBy, String updatedBy, List<Movement> movements,
                          List<Item> items) {

        static Upload from(JsonNode payload) {
            List<Movement> movements = InventoryUploadWriter.movementsFrom(payload);
            if (movements.isEmpty()) {
                throw new InvalidPayloadException("movements");
            }
            return new Upload(longField(payload, "branchId"), text(payload, "createdBy"),
                    text(payload, "updatedBy"), movements, InventoryUploadWriter.itemsFrom(payload));
        }

        /** Every product the batch touches: in its movements and its stock rows. */
        List<Long> productIds() {
            List<Long> ids = new ArrayList<>();
            movements.forEach(movement -> ids.add(movement.productId()));
            items.forEach(item -> ids.add(item.productId()));
            return ids.stream().distinct().toList();
        }
    }
}
