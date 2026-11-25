package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.SaleableProduct;
import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.StockReferenceType;
import com.medinasystems.pos.inventory.domain.InventoryDocumentType;
import com.medinasystems.pos.inventory.domain.InventoryItem;
import com.medinasystems.pos.inventory.domain.InventoryMovement;
import com.medinasystems.pos.inventory.infrastructure.InventoryDocumentRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryDocumentSpecifications;
import com.medinasystems.pos.inventory.infrastructure.InventoryItemRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryItemSpecifications;
import com.medinasystems.pos.inventory.infrastructure.InventoryMovementRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryMovementSpecifications;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.sales.SaleReceipts;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.BusinessRuleException;
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
    /** Largest number of products one availability request may ask for. */
    static final int MAX_AVAILABILITY_PRODUCTS = 100;

    private final InventoryItemRepository items;
    private final InventoryMovementRepository movements;
    private final InventoryDocumentRepository documents;
    private final BranchScope scope;
    private final BranchDirectory branchDirectory;
    private final UserDirectory users;
    private final SaleableProducts products;
    private final SaleReceipts receipts;

    public InventoryQueryService(InventoryItemRepository items, InventoryMovementRepository movements,
                                 InventoryDocumentRepository documents, BranchScope scope,
                                 BranchDirectory branchDirectory, UserDirectory users, SaleableProducts products,
                                 SaleReceipts receipts) {
        this.items = items;
        this.movements = movements;
        this.documents = documents;
        this.scope = scope;
        this.branchDirectory = branchDirectory;
        this.users = users;
        this.products = products;
        this.receipts = receipts;
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

    /**
     * The stock a sale can use for each product, in the node's branch (a store) or in {@code branchId} (the central node,
     * which requires it). Answers at most {@value #MAX_AVAILABILITY_PRODUCTS} products; an unknown product answers 404.
     */
    public List<ProductAvailabilityView> availability(List<Long> productIds, Long branchId) {
        if (productIds.size() > MAX_AVAILABILITY_PRODUCTS) {
            throw new BusinessRuleException("AVAILABILITY_TOO_MANY_PRODUCTS",
                    "A request may ask for at most " + MAX_AVAILABILITY_PRODUCTS + " products", MAX_AVAILABILITY_PRODUCTS);
        }
        List<Long> ids = productIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        long branch = availabilityBranch(branchId);
        BranchInfo info = branchDirectory.info(branch)
                .orElseThrow(() -> new ResourceNotFoundException("branch", branch));
        Map<Long, InventoryItem> stock = new HashMap<>();
        for (InventoryItem item : items.findByBranchIdAndProductIdIn(branch, ids)) {
            stock.put(item.getProductId(), item);
        }
        List<ProductAvailabilityView> result = new ArrayList<>();
        for (long productId : ids) {
            SaleableProduct product = products.find(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("product", productId));
            boolean tracks = product.tracksInventory() && !"SERVICE".equals(product.productType());
            InventoryItem item = stock.get(productId);
            BigDecimal quantity = tracks && item != null ? item.getQuantity() : BigDecimal.ZERO;
            result.add(new ProductAvailabilityView(productId, tracks, quantity, info.allowNegativeStock()));
        }
        return result;
    }

    /**
     * The branch an availability request reads. A store reads its own branch and answers 404 for another one; the central
     * node needs the branch named.
     */
    private long availabilityBranch(Long requested) {
        if (scope.allBranches()) {
            if (requested == null) {
                throw new BusinessRuleException("INVENTORY_BRANCH_REQUIRED", "Choose the branch of the document");
            }
            return requested;
        }
        long own = scope.nodeBranchId().orElseThrow(() -> new BusinessRuleException("INVENTORY_BRANCH_NOT_OPERATED",
                "This node does not operate the inventory of the branch"));
        if (requested != null && requested != own) {
            throw new ResourceNotFoundException("branch", requested);
        }
        return own;
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
        Page<InventoryMovement> page = movements.findAll(
                InventoryMovementSpecifications.matching(filter.branchId(), productId, type, from, to), pageable);
        // One lookup for the whole page: the receipts of its sales and returns.
        List<Long> saleIds = page.getContent().stream()
                .filter(movement -> movement.getReferenceType() == StockReferenceType.SALE
                        || movement.getReferenceType() == StockReferenceType.SALE_RETURN)
                .map(InventoryMovement::getReferenceId)
                .distinct()
                .toList();
        Map<Long, String> receiptNumbers = receipts.receiptNumbers(saleIds);
        return page.map(movement -> StockMovementView.of(movement, names, receiptNumbers));
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
