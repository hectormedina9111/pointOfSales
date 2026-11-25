package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.SaleableProduct;
import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.inventory.StockLedger;
import com.medinasystems.pos.inventory.StockMovement;
import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.StockReferenceType;
import com.medinasystems.pos.inventory.domain.InventoryItem;
import com.medinasystems.pos.inventory.domain.InventoryMovement;
import com.medinasystems.pos.inventory.domain.StockCalculator;
import com.medinasystems.pos.inventory.infrastructure.InventoryItemRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryMovementRepository;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * The only place where stock moves. Each movement locks the stock row of the product in the branch, so two movements of
 * one product run one after the other. The rules:
 *
 * <ul>
 *   <li>the node operates the branch; the product tracks inventory, has no serials and is active;</li>
 *   <li>an incoming movement takes the given cost, or the average, or the product cost, and updates the weighted average
 *       (RETURN_IN does not change it);</li>
 *   <li>an outgoing movement leaves at the average and cannot leave a negative balance unless the branch allows it.</li>
 * </ul>
 *
 * <p>It must run inside a transaction: the lock lasts until the transaction ends.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class DefaultStockLedger implements StockLedger {

    private static final String SYSTEM_AUDITOR = "system";

    private final InventoryItemRepository items;
    private final InventoryMovementRepository movements;
    private final SaleableProducts products;
    private final InventoryBranches branches;
    private final AuditorAware<String> auditor;
    private final Clock clock;

    public DefaultStockLedger(InventoryItemRepository items, InventoryMovementRepository movements,
                       SaleableProducts products, InventoryBranches branches, AuditorAware<String> auditor,
                       Clock clock) {
        this.items = items;
        this.movements = movements;
        this.products = products;
        this.branches = branches;
        this.auditor = auditor;
        this.clock = clock;
    }

    @Override
    public StockMovement apply(long branchId, long productId, StockMovementType type, BigDecimal signedQuantity,
                               BigDecimal unitCost, StockReferenceType referenceType, long referenceId, long userId) {
        BranchInfo branch = branches.operated(branchId);
        SaleableProduct product = inventoryProduct(productId);
        if (signedQuantity == null || signedQuantity.signum() == 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "The quantity must be greater than zero");
        }
        if (type.incoming() != null && type.incoming() != (signedQuantity.signum() > 0)) {
            throw new IllegalArgumentException("The sign of the quantity does not match the movement type " + type);
        }
        BigDecimal quantity = StockCalculator.quantity(signedQuantity, product.allowsDecimals());
        InventoryItem item = lock(branchId, product);
        return move(item, branch, product, type, quantity, unitCost, referenceType, referenceId, userId);
    }

    @Override
    public Optional<StockMovement> count(long branchId, long productId, BigDecimal countedQuantity,
                                         StockReferenceType referenceType, long referenceId, long userId) {
        BranchInfo branch = branches.operated(branchId);
        SaleableProduct product = inventoryProduct(productId);
        if (countedQuantity == null || countedQuantity.signum() < 0) {
            throw new BusinessRuleException("INVALID_COUNTED_QUANTITY", "The counted quantity cannot be negative");
        }
        BigDecimal counted = StockCalculator.quantity(countedQuantity, product.allowsDecimals());
        InventoryItem item = lock(branchId, product);
        BigDecimal change = counted.subtract(item.getQuantity());
        if (change.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(move(item, branch, product, StockMovementType.ADJUSTMENT, change, null, referenceType,
                referenceId, userId));
    }

    /**
     * The product as the inventory needs it. Services and products that do not track inventory are refused, and so are
     * products with serials, which are not supported yet.
     */
    private SaleableProduct inventoryProduct(long productId) {
        SaleableProduct product = products.find(productId)
                .orElseThrow(() -> new BusinessRuleException("PRODUCT_NOT_FOUND", "The product does not exist"));
        if (!product.active()) {
            throw new BusinessRuleException("PRODUCT_INACTIVE", "The product is inactive");
        }
        if ("SERVICE".equals(product.productType()) || !product.tracksInventory()) {
            throw new BusinessRuleException("PRODUCT_NOT_INVENTORIED", "The product does not track inventory");
        }
        if (product.tracksSerials()) {
            throw new BusinessRuleException("SERIALS_REQUIRED", "The product needs serial numbers, not supported yet");
        }
        return product;
    }

    /** The stock row of the product in the branch, created when missing and locked until the transaction ends. */
    private InventoryItem lock(long branchId, SaleableProduct product) {
        String by = auditor.getCurrentAuditor().orElse(SYSTEM_AUDITOR);
        items.insertIfAbsent(product.id(), branchId, product.sku(), product.name(), product.categoryId(),
                product.minStock(), clock.instant(), by);
        return items.findForUpdate(product.id(), branchId)
                .orElseThrow(() -> new IllegalStateException("The stock row of the product is missing"));
    }

    /**
     * Applies a change that was already checked for sign and precision: it updates the stock row and writes the movement.
     *
     * @param change        the signed change, with the precision of the unit
     * @param requestedCost the cost of an incoming ENTRY or TRANSFER_IN, or null to use the average or the product cost
     */
    private StockMovement move(InventoryItem item, BranchInfo branch, SaleableProduct product, StockMovementType type,
                               BigDecimal change, BigDecimal requestedCost, StockReferenceType referenceType,
                               long referenceId, long userId) {
        BigDecimal quantity = change.setScale(StockCalculator.QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        BigDecimal before = item.getQuantity();
        BigDecimal after = before.add(quantity);
        boolean incoming = quantity.signum() > 0;
        if (!incoming && after.signum() < 0 && !branch.allowNegativeStock()) {
            throw new BusinessRuleException("INSUFFICIENT_STOCK",
                    "There is not enough stock of " + product.name() + " (SKU " + product.sku() + ")",
                    product.sku(), product.name(), before.stripTrailingZeros().toPlainString());
        }
        BigDecimal average = item.getAverageCost();
        BigDecimal unitCost;
        BigDecimal newAverage = average;
        if (!incoming) {
            unitCost = average;
        } else if (type == StockMovementType.RETURN_IN) {
            unitCost = costWhenNoAverage(average, product);
        } else {
            unitCost = requestedCost != null
                    ? StockCalculator.cost(requestedCost)
                    : costWhenNoAverage(average, product);
            newAverage = StockCalculator.averageAfterEntry(before, average, quantity, unitCost);
        }
        Instant now = clock.instant();
        item.applyMovement(after, newAverage, product, now);
        InventoryMovement movement = movements.save(new InventoryMovement(branch.branchId(), product.id(), type,
                quantity, after, unitCost, newAverage, referenceType, referenceId, now, userId));
        return new StockMovement(movement.getId(), movement.getBranchId(), movement.getProductId(),
                movement.getType(), movement.getQuantity(), movement.getResultingBalance(), movement.getUnitCost(),
                movement.getResultingAverageCost(), movement.getReferenceType(), movement.getReferenceId(),
                movement.getMovedAt(), movement.getUserId());
    }

    /** The current average when there is one; otherwise the product cost, or zero when the product has none. */
    private static BigDecimal costWhenNoAverage(BigDecimal average, SaleableProduct product) {
        if (average.signum() > 0) {
            return average;
        }
        return product.cost() == null ? StockCalculator.cost(BigDecimal.ZERO) : StockCalculator.cost(product.cost());
    }
}
