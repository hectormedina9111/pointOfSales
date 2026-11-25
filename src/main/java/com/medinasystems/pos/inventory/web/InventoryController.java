package com.medinasystems.pos.inventory.web;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.application.InventoryItemView;
import com.medinasystems.pos.inventory.application.InventoryQueryService;
import com.medinasystems.pos.inventory.application.StockMovementView;
import com.medinasystems.pos.shared.web.PageResponse;

/**
 * Stock of the branches the node sees, and its movement log. The lists are paged and sorted in the database. A store sees
 * only its own branch; the central node sees every branch.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryQueryService queries;

    public InventoryController(InventoryQueryService queries) {
        this.queries = queries;
    }

    /**
     * {@code search} matches the sku or the name. {@code belowMin=true} lists the products under their minimum,
     * {@code belowMin=false} the others.
     */
    @GetMapping
    public PageResponse<InventoryItemView> stock(@RequestParam(required = false) Long branchId,
                                                 @RequestParam(required = false) String search,
                                                 @RequestParam(required = false) Long categoryId,
                                                 @RequestParam(required = false) Boolean belowMin,
                                                 @PageableDefault(sort = {"productName", "id"}) Pageable pageable) {
        return PageResponse.from(queries.stock(branchId, search, categoryId, belowMin, pageable));
    }

    @GetMapping("/{productId}/branches/{branchId}")
    public InventoryItemView stockOf(@PathVariable long productId, @PathVariable long branchId) {
        return queries.stockOf(productId, branchId);
    }

    @GetMapping("/movements")
    public PageResponse<StockMovementView> movements(@RequestParam(required = false) Long productId,
                                                     @RequestParam(required = false) Long branchId,
                                                     @RequestParam(required = false) StockMovementType type,
                                                     @RequestParam(required = false) Instant from,
                                                     @RequestParam(required = false) Instant to,
                                                     @PageableDefault(sort = {"movedAt", "id"},
                                                             direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(queries.movements(productId, branchId, type, from, to, pageable));
    }
}
