package com.medinasystems.pos.inventory.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.inventory.StockReferenceType;
import com.medinasystems.pos.inventory.application.SerialTraceView.MovementView;
import com.medinasystems.pos.inventory.domain.InventoryMovement;
import com.medinasystems.pos.inventory.domain.InventoryMovementSerial;
import com.medinasystems.pos.inventory.domain.ProductSerial;
import com.medinasystems.pos.inventory.domain.SerialStatus;
import com.medinasystems.pos.inventory.infrastructure.InventoryDocumentRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryMovementRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryMovementSerialRepository;
import com.medinasystems.pos.inventory.infrastructure.ProductSerialRepository;
import com.medinasystems.pos.inventory.infrastructure.ProductSerialSpecifications;
import com.medinasystems.pos.inventory.infrastructure.InventoryTransferRepository;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.sales.SaleReceipts;
import com.medinasystems.pos.sales.SaleSerials;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Reads of the serial units: the units of a product, filtered and paged in the database, and the trace of one serial
 * number with its movements. Each read is limited to the branches the node sees.
 */
@Service
@Transactional(readOnly = true)
public class SerialQueryService {

    private static final Set<String> SERIAL_SORT = Set.of("serialNumber", "receivedAt", "soldAt", "id");

    private final ProductSerialRepository units;
    private final InventoryMovementSerialRepository links;
    private final InventoryMovementRepository movements;
    private final InventoryDocumentRepository documents;
    private final BranchScope scope;
    private final BranchDirectory branchDirectory;
    private final UserDirectory users;
    private final SaleableProducts products;
    private final SaleReceipts receipts;
    private final InventoryTransferRepository transfers;
    private final SaleSerials saleSerials;

    public SerialQueryService(ProductSerialRepository units, InventoryMovementSerialRepository links,
                              InventoryMovementRepository movements, InventoryDocumentRepository documents,
                              BranchScope scope, BranchDirectory branchDirectory, UserDirectory users,
                              SaleableProducts products, SaleReceipts receipts,
                              InventoryTransferRepository transfers, SaleSerials saleSerials) {
        this.transfers = transfers;
        this.saleSerials = saleSerials;
        this.units = units;
        this.links = links;
        this.movements = movements;
        this.documents = documents;
        this.scope = scope;
        this.branchDirectory = branchDirectory;
        this.users = users;
        this.products = products;
        this.receipts = receipts;
    }

    /**
     * The serial units of one product in the branches the node sees, with the optional branch, status and search filters.
     *
     * @throws ResourceNotFoundException when the product does not exist
     */
    public Page<SerialView> units(long productId, SerialStatus status, Long branchId, String search, Pageable pageable) {
        InventorySorting.requireSortable(pageable.getSort(), SERIAL_SORT);
        if (products.find(productId).isEmpty()) {
            throw new ResourceNotFoundException("product", productId);
        }
        Visible visible = visibleBranch(branchId);
        if (visible.nothing()) {
            return Page.empty(pageable);
        }
        InventoryNames names = names();
        return units.findAll(ProductSerialSpecifications.forProduct(productId, visible.branchId(), status, search),
                pageable)
                .map(unit -> SerialView.of(unit, names));
    }

    /**
     * Every unit with this serial number (without regard to case): the equipment of products with serial mode LIST, with
     * its last sale and its movements, and the sales of products with serial mode MANUAL. A blank serial number finds
     * nothing.
     */
    public List<SerialTraceView> trace(String serialNumber, Long productId) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return List.of();
        }
        Visible visible = visibleBranch(null);
        if (visible.nothing()) {
            return List.of();
        }
        List<SerialTraceView> views = new ArrayList<>(equipmentTrace(serialNumber.trim(), productId, visible));
        views.addAll(manualSaleTrace(serialNumber.trim(), productId, visible));
        return views;
    }

    /** The equipment units with this serial number in the visible branches, each with its movements. */
    private List<SerialTraceView> equipmentTrace(String serialNumber, Long productId, Visible visible) {
        List<ProductSerial> found = units.findAll(
                ProductSerialSpecifications.trace(serialNumber, productId, visible.branchId()));
        if (found.isEmpty()) {
            return List.of();
        }
        Map<ProductSerial, List<InventoryMovementSerial>> trails = new HashMap<>();
        Set<Long> movementIds = new LinkedHashSet<>();
        for (ProductSerial unit : found) {
            List<InventoryMovementSerial> trail = links.findBySerialIdOrderByIdAsc(unit.getId());
            trails.put(unit, trail);
            trail.forEach(link -> movementIds.add(link.getMovementId()));
        }
        Map<Long, InventoryMovement> byId = movements.findAllById(movementIds).stream()
                .collect(Collectors.toMap(InventoryMovement::getId, movement -> movement));
        Set<Long> saleIds = new LinkedHashSet<>();
        found.forEach(unit -> {
            if (unit.getLastSaleId() != null) {
                saleIds.add(unit.getLastSaleId());
            }
        });
        byId.values().stream().filter(movement -> isSale(movement.getReferenceType()))
                .forEach(movement -> saleIds.add(movement.getReferenceId()));
        Map<Long, String> receiptNumbers = saleIds.isEmpty() ? Map.of() : receipts.receiptNumbers(saleIds);
        InventoryNames names = names();
        List<SerialTraceView> views = new ArrayList<>();
        for (ProductSerial unit : found) {
            List<MovementView> trail = new ArrayList<>();
            for (InventoryMovementSerial link : trails.get(unit)) {
                InventoryMovement movement = byId.get(link.getMovementId());
                if (movement != null) {
                    trail.add(movementView(movement, names, receiptNumbers));
                }
            }
            views.add(new SerialTraceView(unit.getId(), unit.getProductId(), names.productSku(unit.getProductId()),
                    names.productName(unit.getProductId()), unit.getSerialNumber(), unit.getStatus().name(),
                    unit.getBranchId(), names.branchName(unit.getBranchId()), unit.getReceivedAt(), unit.getSoldAt(),
                    unit.getLastSaleId(), unit.getLastSaleId() == null ? null : receiptNumbers.get(unit.getLastSaleId()),
                    unit.getExternalCode(), unit.getAttributes(), trail, SerialTraceView.SOURCE_EQUIPMENT));
        }
        return views;
    }

    /**
     * The sales of MANUAL products with this serial number, in the branches the node sees. The status is SOLD, or RETURNED
     * once the unit came back. The sale knows nothing else about the unit, so the entry has no id or movements.
     */
    private List<SerialTraceView> manualSaleTrace(String serialNumber, Long productId, Visible visible) {
        InventoryNames names = names();
        List<SerialTraceView> views = new ArrayList<>();
        for (SaleSerials.ManualSerialSale sale : saleSerials.manualSerialSales(serialNumber)) {
            boolean sameProduct = productId == null || sale.productId() == productId;
            boolean sameBranch = visible.branchId() == null || sale.branchId() == visible.branchId();
            if (!sameProduct || !sameBranch) {
                continue;
            }
            views.add(new SerialTraceView(null, sale.productId(), names.productSku(sale.productId()),
                    names.productName(sale.productId()), serialNumber, sale.returned() ? "RETURNED" : "SOLD",
                    sale.branchId(), names.branchName(sale.branchId()), null, sale.soldAt(), sale.saleId(),
                    sale.receiptNumber(), null, Map.of(), List.of(), SerialTraceView.SOURCE_SALE));
        }
        return views;
    }

    private static MovementView movementView(InventoryMovement movement, InventoryNames names,
                                             Map<Long, String> receiptNumbers) {
        String referenceNumber = switch (movement.getReferenceType()) {
            case INVENTORY_DOCUMENT -> names.documentNumber(movement.getReferenceId());
            case SALE, SALE_RETURN -> receiptNumbers.get(movement.getReferenceId());
            case TRANSFER -> names.transferNumber(movement.getReferenceId());
        };
        return new MovementView(movement.getId(), movement.getType(), movement.getMovedAt(),
                names.branchName(movement.getBranchId()), movement.getReferenceType(), movement.getReferenceId(),
                referenceNumber);
    }

    private static boolean isSale(StockReferenceType type) {
        return type == StockReferenceType.SALE || type == StockReferenceType.SALE_RETURN;
    }

    /**
     * The branch a read is limited to: a store sees its own branch only (another branch answers nothing); the central node
     * sees every branch unless one is requested.
     */
    private Visible visibleBranch(Long requested) {
        if (scope.allBranches()) {
            return new Visible(false, requested);
        }
        var own = scope.nodeBranchId();
        if (own.isEmpty() || (requested != null && !requested.equals(own.get()))) {
            return new Visible(true, null);
        }
        return new Visible(false, own.get());
    }

    /** The branches a read can see: none, or one branch, or null for all of them. */
    private record Visible(boolean nothing, Long branchId) {
    }

    private InventoryNames names() {
        return new InventoryNames(branchDirectory, users, products, documents, transfers);
    }
}
