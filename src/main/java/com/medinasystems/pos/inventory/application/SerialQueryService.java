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
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.sales.SaleReceipts;
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

    public SerialQueryService(ProductSerialRepository units, InventoryMovementSerialRepository links,
                              InventoryMovementRepository movements, InventoryDocumentRepository documents,
                              BranchScope scope, BranchDirectory branchDirectory, UserDirectory users,
                              SaleableProducts products, SaleReceipts receipts) {
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
     * Every unit with this serial number (without regard to case), with its last sale and its movements. A blank serial
     * number finds nothing.
     */
    public List<SerialTraceView> trace(String serialNumber, Long productId) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return List.of();
        }
        Visible visible = visibleBranch(null);
        if (visible.nothing()) {
            return List.of();
        }
        List<ProductSerial> found = units.findAll(
                ProductSerialSpecifications.trace(serialNumber.trim(), productId, visible.branchId()));
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
                    names.productName(unit.getProductId()), unit.getSerialNumber(), unit.getStatus(),
                    unit.getBranchId(), names.branchName(unit.getBranchId()), unit.getReceivedAt(), unit.getSoldAt(),
                    unit.getLastSaleId(), unit.getLastSaleId() == null ? null : receiptNumbers.get(unit.getLastSaleId()),
                    trail));
        }
        return views;
    }

    private static MovementView movementView(InventoryMovement movement, InventoryNames names,
                                             Map<Long, String> receiptNumbers) {
        String referenceNumber = switch (movement.getReferenceType()) {
            case INVENTORY_DOCUMENT -> names.documentNumber(movement.getReferenceId());
            case SALE, SALE_RETURN -> receiptNumbers.get(movement.getReferenceId());
            case TRANSFER -> null;
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
        return new InventoryNames(branchDirectory, users, products, documents);
    }
}
