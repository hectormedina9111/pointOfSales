package com.medinasystems.pos.inventory.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.medinasystems.pos.inventory.StockMovement;
import com.medinasystems.pos.inventory.domain.InventoryItem;
import com.medinasystems.pos.inventory.infrastructure.InventoryItemRepository;

/** The stock rows of the products that some movements changed, as they are after those movements. */
final class ResultingStock {

    private ResultingStock() {
    }

    /** One stock row per product moved, in the order of the movements. */
    static List<InventoryItem> of(InventoryItemRepository items, List<StockMovement> moved, long branchId) {
        Map<Long, InventoryItem> byProduct = new LinkedHashMap<>();
        for (StockMovement movement : moved) {
            byProduct.computeIfAbsent(movement.productId(), productId -> items.findByProductIdAndBranchId(productId,
                    branchId).orElseThrow(() -> new IllegalStateException("The stock row of the product is missing")));
        }
        return new ArrayList<>(byProduct.values());
    }
}
