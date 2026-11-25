package com.medinasystems.pos.inventory.application;

import java.time.Instant;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.domain.InventoryDocumentType;
import com.medinasystems.pos.inventory.infrastructure.InventoryDocumentRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryDocumentSpecifications;
import com.medinasystems.pos.inventory.infrastructure.InventoryItemRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryItemSpecifications;
import com.medinasystems.pos.inventory.infrastructure.InventoryMovementRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryMovementSpecifications;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Reads of the inventory: the stock, the movements and the documents. Each read is limited to the branches the node sees;
 * the filters, the sort and the paging run in the database.
 */
@Service
@Transactional(readOnly = true)
public class InventoryQueryService {

    private static final Set<String> STOCK_SORT = Set.of("productName", "productSku", "quantity", "lastMovementAt");
    private static final Set<String> MOVEMENT_SORT = Set.of("movedAt", "id", "quantity");
    private static final Set<String> DOCUMENT_SORT = Set.of("postedAt", "number", "id");

    private final InventoryItemRepository items;
    private final InventoryMovementRepository movements;
    private final InventoryDocumentRepository documents;
    private final BranchScope scope;
    private final BranchDirectory branchDirectory;
    private final UserDirectory users;
    private final SaleableProducts products;

    public InventoryQueryService(InventoryItemRepository items, InventoryMovementRepository movements,
                                 InventoryDocumentRepository documents, BranchScope scope,
                                 BranchDirectory branchDirectory, UserDirectory users, SaleableProducts products) {
        this.items = items;
        this.movements = movements;
        this.documents = documents;
        this.scope = scope;
        this.branchDirectory = branchDirectory;
        this.users = users;
        this.products = products;
    }

    /**
     * The stock of the visible branches. {@code belowMin} true lists the products under their minimum; false lists the
     * others; null lists all.
     */
    public Page<InventoryItemView> stock(Long branchId, String search, Long categoryId, Boolean belowMin,
                                         Pageable pageable) {
        InventorySorting.requireSortable(pageable.getSort(), STOCK_SORT);
        BranchFilter filter = branchFilter(branchId);
        if (filter.nothingVisible()) {
            return Page.empty(pageable);
        }
        InventoryNames names = names();
        return items.findAll(InventoryItemSpecifications.matching(filter.branchId(), search, categoryId, belowMin),
                pageable).map(item -> InventoryItemView.of(item, names));
    }

    /** The stock of one product in one branch. Answers 404 when the branch is not visible or has no stock row. */
    public InventoryItemView stockOf(long productId, long branchId) {
        if (!scope.canSee(branchId)) {
            throw new ResourceNotFoundException("inventory item", productId);
        }
        InventoryNames names = names();
        return items.findByProductIdAndBranchId(productId, branchId)
                .map(item -> InventoryItemView.of(item, names))
                .orElseThrow(() -> new ResourceNotFoundException("inventory item", productId));
    }

    /** The movements of the visible branches, between {@code from} and {@code to} when set. */
    public Page<StockMovementView> movements(Long productId, Long branchId, StockMovementType type, Instant from,
                                             Instant to, Pageable pageable) {
        InventorySorting.requireSortable(pageable.getSort(), MOVEMENT_SORT);
        BranchFilter filter = branchFilter(branchId);
        if (filter.nothingVisible()) {
            return Page.empty(pageable);
        }
        InventoryNames names = names();
        return movements.findAll(InventoryMovementSpecifications.matching(filter.branchId(), productId, type, from, to),
                pageable).map(movement -> StockMovementView.of(movement, names));
    }

    /** The documents of the visible branches. {@code search} matches part of the number. */
    public Page<InventoryDocumentSummaryView> documents(Long branchId, InventoryDocumentType type, String search,
                                                        Instant from, Instant to, Pageable pageable) {
        InventorySorting.requireSortable(pageable.getSort(), DOCUMENT_SORT);
        BranchFilter filter = branchFilter(branchId);
        if (filter.nothingVisible()) {
            return Page.empty(pageable);
        }
        InventoryNames names = names();
        return documents.findAll(InventoryDocumentSpecifications.matching(filter.branchId(), type, search, from, to),
                pageable).map(document -> InventoryDocumentSummaryView.of(document, names));
    }

    /** One document with its lines. Answers 404 when its branch is not visible. */
    public InventoryDocumentView document(long id) {
        return documents.findById(id)
                .filter(document -> scope.canSee(document.getBranchId()))
                .map(document -> InventoryDocumentView.of(document, names()))
                .orElseThrow(() -> new ResourceNotFoundException("inventory document", id));
    }

    /**
     * The branch a list is restricted to. A node that sees all branches filters by the requested one, if any. A store
     * sees only its own: asking for another branch, or having none, shows nothing.
     */
    private BranchFilter branchFilter(Long requested) {
        if (scope.allBranches()) {
            return new BranchFilter(false, requested);
        }
        var own = scope.nodeBranchId();
        if (own.isEmpty() || (requested != null && !requested.equals(own.get()))) {
            return new BranchFilter(true, null);
        }
        return new BranchFilter(false, own.get());
    }

    private InventoryNames names() {
        return new InventoryNames(branchDirectory, users, products, documents);
    }

    /** The branch a list is restricted to: null when it is not restricted. */
    private record BranchFilter(boolean nothingVisible, Long branchId) {
    }
}
