package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.medinasystems.pos.inventory.domain.InventoryMovementSerial;
import com.medinasystems.pos.inventory.domain.ProductSerial;
import com.medinasystems.pos.inventory.domain.SerialStatus;
import com.medinasystems.pos.inventory.domain.StockCalculator;
import com.medinasystems.pos.inventory.infrastructure.InventoryItemRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryMovementRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryMovementSerialRepository;
import com.medinasystems.pos.inventory.infrastructure.ProductSerialRepository;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.SerialNumbers;

/**
 * The only place where stock moves. Each movement locks the stock row of the product in the branch, so two movements of
 * one product run one after the other. The rules:
 *
 * <ul>
 *   <li>the node operates the branch; the product tracks inventory and is active;</li>
 *   <li>an incoming movement takes the given cost, or the average, or the product cost, and updates the weighted average
 *       (RETURN_IN does not change it);</li>
 *   <li>an outgoing movement leaves at the average and cannot leave a negative balance unless the branch allows it;</li>
 *   <li>a product that tracks serial numbers moves one unit per serial: an incoming unit must not be available anywhere
 *       else, an outgoing unit must be available in this branch, and the negative stock rule does not apply.</li>
 * </ul>
 *
 * <p>It must run inside a transaction: the lock lasts until the transaction ends.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class DefaultStockLedger implements StockLedger {

    private static final String SYSTEM_AUDITOR = "system";
    private static final String SERVICE = "SERVICE";

    private final InventoryItemRepository items;
    private final InventoryMovementRepository movements;
    private final ProductSerialRepository serialUnits;
    private final InventoryMovementSerialRepository serialLinks;
    private final SaleableProducts products;
    private final InventoryBranches branches;
    private final AuditorAware<String> auditor;
    private final Clock clock;

    public DefaultStockLedger(InventoryItemRepository items, InventoryMovementRepository movements,
                              ProductSerialRepository serialUnits, InventoryMovementSerialRepository serialLinks,
                              SaleableProducts products, InventoryBranches branches, AuditorAware<String> auditor,
                              Clock clock) {
        this.items = items;
        this.movements = movements;
        this.serialUnits = serialUnits;
        this.serialLinks = serialLinks;
        this.products = products;
        this.branches = branches;
        this.auditor = auditor;
        this.clock = clock;
    }

    @Override
    public StockMovement apply(long branchId, long productId, StockMovementType type, BigDecimal signedQuantity,
                               BigDecimal unitCost, List<String> serialNumbers, StockReferenceType referenceType,
                               long referenceId, long userId) {
        BranchInfo branch = branches.operated(branchId);
        SaleableProduct product = inventoryProduct(productId, referenceType);
        if (signedQuantity == null || signedQuantity.signum() == 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "The quantity must be greater than zero");
        }
        if (type.incoming() != null && type.incoming() != (signedQuantity.signum() > 0)) {
            throw new IllegalArgumentException("The sign of the quantity does not match the movement type " + type);
        }
        if (product.tracksSerials()) {
            List<String> serials = SerialNumbers.normalize(serialNumbers);
            SerialNumbers.requireOnePerUnit(signedQuantity, serials);
            BigDecimal units = StockCalculator.quantity(signedQuantity, product.allowsDecimals());
            InventoryItem item = lock(branchId, product);
            return serialMove(item, branch, product, type, units, unitCost, serials, referenceType, referenceId, userId);
        }
        BigDecimal quantity = StockCalculator.quantity(signedQuantity, product.allowsDecimals());
        InventoryItem item = lock(branchId, product);
        return move(item, branch, product, type, quantity, unitCost, referenceType, referenceId, userId, true);
    }

    @Override
    public List<StockMovement> count(long branchId, long productId, BigDecimal countedQuantity,
                                     List<String> serialNumbers, StockReferenceType referenceType, long referenceId,
                                     long userId) {
        BranchInfo branch = branches.operated(branchId);
        SaleableProduct product = inventoryProduct(productId, referenceType);
        if (countedQuantity == null || countedQuantity.signum() < 0) {
            throw new BusinessRuleException("INVALID_COUNTED_QUANTITY", "The counted quantity cannot be negative");
        }
        if (product.tracksSerials()) {
            return countSerials(branch, product, countedQuantity, SerialNumbers.normalize(serialNumbers),
                    referenceType, referenceId, userId);
        }
        BigDecimal counted = StockCalculator.quantity(countedQuantity, product.allowsDecimals());
        InventoryItem item = lock(branchId, product);
        BigDecimal change = counted.subtract(item.getQuantity());
        if (change.signum() == 0) {
            return List.of();
        }
        return List.of(move(item, branch, product, StockMovementType.ADJUSTMENT, change, null, referenceType,
                referenceId, userId, true));
    }

    /**
     * The product as the inventory needs it. Services and products that do not track inventory are refused. A return
     * always restocks and a transfer must be able to arrive, so both accept a product deactivated later; a sale does not.
     */
    private SaleableProduct inventoryProduct(long productId, StockReferenceType referenceType) {
        SaleableProduct product = products.find(productId)
                .orElseThrow(() -> new BusinessRuleException("PRODUCT_NOT_FOUND", "The product does not exist"));
        if (!product.active() && referenceType != StockReferenceType.SALE_RETURN
                && referenceType != StockReferenceType.TRANSFER) {
            throw new BusinessRuleException("PRODUCT_INACTIVE", "The product is inactive");
        }
        if (SERVICE.equals(product.productType()) || !product.tracksInventory()) {
            throw new BusinessRuleException("PRODUCT_NOT_INVENTORIED", "The product does not track inventory");
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
     * @param checkStock    false for a product with serials, whose units are checked one by one instead
     */
    private StockMovement move(InventoryItem item, BranchInfo branch, SaleableProduct product, StockMovementType type,
                               BigDecimal change, BigDecimal requestedCost, StockReferenceType referenceType,
                               long referenceId, long userId, boolean checkStock) {
        BigDecimal quantity = change.setScale(StockCalculator.QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        BigDecimal before = item.getQuantity();
        BigDecimal after = before.add(quantity);
        boolean incoming = quantity.signum() > 0;
        if (checkStock && !incoming && after.signum() < 0 && !branch.allowNegativeStock()) {
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

    /**
     * One movement of a product with serials: each unit is checked, the stock moves, and each unit is updated and linked to
     * the movement. An incoming unit that is not known is created; an outgoing unit must be available in this branch.
     */
    private StockMovement serialMove(InventoryItem item, BranchInfo branch, SaleableProduct product,
                                     StockMovementType type, BigDecimal units, BigDecimal requestedCost,
                                     List<String> serials, StockReferenceType referenceType, long referenceId,
                                     long userId) {
        boolean incoming = units.signum() > 0;
        Map<String, ProductSerial> rows = rowsByNumber(product.id(), serials);
        for (String number : serials) {
            ProductSerial row = rows.get(number);
            if (incoming) {
                requireNotAvailable(row, number, type == StockMovementType.TRANSFER_IN);
            } else if (row == null || row.getBranchId() != branch.branchId() || row.getStatus() != SerialStatus.AVAILABLE) {
                throw new BusinessRuleException("SERIAL_NOT_AVAILABLE",
                        "The serial number is not available in this branch", number);
            }
        }
        StockMovement moved = move(item, branch, product, type, units, requestedCost, referenceType, referenceId,
                userId, false);
        Instant now = clock.instant();
        for (String number : serials) {
            ProductSerial row = rows.get(number);
            if (incoming) {
                if (row == null) {
                    row = serialUnits.save(new ProductSerial(product.id(), product.categoryId(), number, branch.branchId(), now));
                }
                row.enter(branch.branchId(), now, moved.id());
            } else if (type == StockMovementType.SALE_OUT) {
                row.sell(referenceId, now);
            } else if (type == StockMovementType.TRANSFER_OUT) {
                row.dispatch();
            } else {
                row.remove();
            }
            serialLinks.save(new InventoryMovementSerial(moved.id(), row.getId()));
        }
        return moved;
    }

    /**
     * A count of a product with serials. The listed serial numbers are every unit physically present: the units available
     * here that are not listed leave (one movement, negative), and the listed units that are not available here enter (a
     * second movement, positive). Each movement carries one serial number per unit it moves.
     */
    private List<StockMovement> countSerials(BranchInfo branch, SaleableProduct product, BigDecimal counted,
                                             List<String> listed, StockReferenceType referenceType, long referenceId,
                                             long userId) {
        SerialNumbers.requireWhole(counted);
        SerialNumbers.requireCount(counted, listed.size());
        InventoryItem item = lock(branch.branchId(), product);
        List<ProductSerial> available = serialUnits.findForUpdateInBranch(product.id(), branch.branchId(),
                SerialStatus.AVAILABLE);
        Set<String> present = new HashSet<>(listed);
        Set<String> here = available.stream().map(ProductSerial::getSerialNumber).collect(Collectors.toSet());
        List<ProductSerial> leaving = available.stream().filter(row -> !present.contains(row.getSerialNumber())).toList();
        List<String> entering = listed.stream().filter(number -> !here.contains(number)).toList();
        Map<String, ProductSerial> rows = rowsByNumber(product.id(), entering);
        for (String number : entering) {
            requireNotAvailable(rows.get(number), number, false);
        }
        Instant now = clock.instant();
        List<StockMovement> moved = new ArrayList<>();
        if (!leaving.isEmpty()) {
            StockMovement out = move(item, branch, product, StockMovementType.ADJUSTMENT,
                    BigDecimal.valueOf(-leaving.size()), null, referenceType, referenceId, userId, false);
            for (ProductSerial row : leaving) {
                row.remove();
                serialLinks.save(new InventoryMovementSerial(out.id(), row.getId()));
            }
            moved.add(out);
        }
        if (!entering.isEmpty()) {
            StockMovement in = move(item, branch, product, StockMovementType.ADJUSTMENT,
                    BigDecimal.valueOf(entering.size()), null, referenceType, referenceId, userId, false);
            for (String number : entering) {
                ProductSerial row = rows.get(number);
                if (row == null) {
                    row = serialUnits.save(new ProductSerial(product.id(), product.categoryId(), number, branch.branchId(), now));
                }
                row.enter(branch.branchId(), now, in.id());
                serialLinks.save(new InventoryMovementSerial(in.id(), row.getId()));
            }
            moved.add(in);
        }
        return moved;
    }

    /**
     * An incoming unit may not be AVAILABLE or IN_TRANSIT anywhere: a unit that left or was removed may come back. A transfer
     * receipt also accepts a unit that this node sent out and still lists as IN_TRANSIT: the unit is coming back to this
     * branch, and this node does not know where the other nodes have it.
     */
    private static void requireNotAvailable(ProductSerial row, String number, boolean transferReceipt) {
        if (row == null) {
            return;
        }
        SerialStatus status = row.getStatus();
        boolean blocked = status == SerialStatus.AVAILABLE
                || (status == SerialStatus.IN_TRANSIT && !transferReceipt);
        if (blocked) {
            throw new BusinessRuleException("SERIAL_ALREADY_EXISTS", "The serial number already exists", number);
        }
    }

    /** The units with these serial numbers, locked, keyed by serial number. Empty when none is given. */
    private Map<String, ProductSerial> rowsByNumber(long productId, Collection<String> numbers) {
        Map<String, ProductSerial> rows = new HashMap<>();
        if (numbers.isEmpty()) {
            return rows;
        }
        for (ProductSerial row : serialUnits.findForUpdate(productId, numbers)) {
            rows.put(row.getSerialNumber(), row);
        }
        return rows;
    }

    /** The current average when there is one; otherwise the product cost, or zero when the product has none. */
    private static BigDecimal costWhenNoAverage(BigDecimal average, SaleableProduct product) {
        if (average.signum() > 0) {
            return average;
        }
        return product.cost() == null ? StockCalculator.cost(BigDecimal.ZERO) : StockCalculator.cost(product.cost());
    }
}
