package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.catalog.SaleableProduct;
import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.inventory.StockLedger;
import com.medinasystems.pos.inventory.StockMovement;
import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.StockReferenceType;
import com.medinasystems.pos.inventory.infrastructure.InventoryItemRepository;
import com.medinasystems.pos.sales.SalePaid;
import com.medinasystems.pos.sales.SaleReturned;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.sync.Outbox;

/**
 * Moves the stock when sales and returns happen. It runs in the transaction of the sale or the return, so a sale that
 * breaks the stock rule is rolled back as a whole.
 *
 * <p>The quantities of the lines are added per product, and the products are moved in id order, so concurrent sales lock
 * the stock rows in the same order. A sale takes the quantity out (SALE_OUT); a return puts it back (RETURN_IN) in the
 * branch that receives it. Products that do not track inventory (services) are skipped. A store queues one
 * INVENTORY_MOVEMENTS item with the movements of the document, in the same transaction.
 */
@Component
public class SaleStockListener {

    private static final String SYSTEM_AUDITOR = "system";
    private static final String SERVICE = "SERVICE";

    private final StockLedger ledger;
    private final SaleableProducts products;
    private final InventoryItemRepository items;
    private final Outbox outbox;
    private final NodeProperties node;
    private final AuditorAware<String> auditor;

    public SaleStockListener(StockLedger ledger, SaleableProducts products, InventoryItemRepository items,
                             Outbox outbox, NodeProperties node, AuditorAware<String> auditor) {
        this.ledger = ledger;
        this.products = products;
        this.items = items;
        this.outbox = outbox;
        this.node = node;
        this.auditor = auditor;
    }

    /** A sale was charged: its products leave the stock of the branch. */
    @EventListener
    public void onSalePaid(SalePaid event) {
        Map<Long, BigDecimal> quantities = new TreeMap<>();
        Map<Long, List<String>> serials = new HashMap<>();
        for (SalePaid.Line line : event.lines()) {
            quantities.merge(line.productId(), line.quantity(), BigDecimal::add);
            serials.computeIfAbsent(line.productId(), productId -> new ArrayList<>()).addAll(line.serialNumbers());
        }
        move(event.branchId(), event.userId(), quantities, serials, StockMovementType.SALE_OUT,
                StockReferenceType.SALE, event.saleId());
    }

    /** A return was created: its products go back to the stock of the branch that receives them. */
    @EventListener
    public void onSaleReturned(SaleReturned event) {
        Map<Long, BigDecimal> quantities = new TreeMap<>();
        Map<Long, List<String>> serials = new HashMap<>();
        for (SaleReturned.Line line : event.lines()) {
            quantities.merge(line.productId(), line.quantity(), BigDecimal::add);
            serials.computeIfAbsent(line.productId(), productId -> new ArrayList<>()).addAll(line.serialNumbers());
        }
        move(event.branchId(), event.userId(), quantities, serials, StockMovementType.RETURN_IN,
                StockReferenceType.SALE_RETURN, event.returnSaleId());
    }

    /**
     * Applies one movement per product, then queues them when this node is a store. The central node never sells, but a
     * movement made there is not uploaded.
     */
    private void move(long branchId, long userId, Map<Long, BigDecimal> quantities, Map<Long, List<String>> serials,
                      StockMovementType type, StockReferenceType reference, long referenceId) {
        List<StockMovement> moved = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : quantities.entrySet()) {
            if (!tracksInventory(entry.getKey())) {
                continue;
            }
            BigDecimal quantity = entry.getValue();
            BigDecimal signed = type == StockMovementType.SALE_OUT ? quantity.negate() : quantity;
            List<String> numbers = serials.getOrDefault(entry.getKey(), List.of());
            moved.add(ledger.apply(branchId, entry.getKey(), type, signed, null, numbers, reference, referenceId,
                    userId));
        }
        if (moved.isEmpty() || !node.isStore()) {
            return;
        }
        String by = auditor.getCurrentAuditor().orElse(SYSTEM_AUDITOR);
        outbox.enqueue(InventoryMovementsDocument.EVENT_TYPE, InventoryMovementsDocument.of(branchId, by, moved,
                ResultingStock.of(items, moved, branchId)));
    }

    private boolean tracksInventory(long productId) {
        SaleableProduct product = products.find(productId)
                .orElseThrow(() -> new BusinessRuleException("PRODUCT_NOT_FOUND", "The product does not exist"));
        return product.tracksInventory() && !SERVICE.equals(product.productType());
    }
}
