package com.medinasystems.pos.inventory.application;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.AttributeDefinition;
import com.medinasystems.pos.catalog.CategoryAttributes;
import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.inventory.domain.ProductSerial;
import com.medinasystems.pos.inventory.domain.SerialStatus;
import com.medinasystems.pos.inventory.infrastructure.InventoryDocumentRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryTransferRepository;
import com.medinasystems.pos.inventory.infrastructure.ProductSerialRepository;
import com.medinasystems.pos.inventory.infrastructure.ProductSerialSpecifications;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Reads of the equipment: the list, filtered and paged in the database, and the detail of one unit with its movements. A node
 * sees the branches it operates: a store its own branch (units sold there stay in it), the central node all of them.
 */
@Service
@Transactional(readOnly = true)
public class EquipmentQueryService {

    private static final Set<String> EQUIPMENT_SORT = Set.of("serialNumber", "receivedAt", "soldAt", "id");
    /** Marks a branch the node does not see, so the answer is empty. No branch has this id. */
    private static final Long NOTHING = -1L;

    private final ProductSerialRepository units;
    private final BranchScope scope;
    private final SerialQueryService serials;
    private final CategoryAttributes attributes;
    private final SaleableProducts products;
    private final BranchDirectory branchDirectory;
    private final UserDirectory users;
    private final InventoryDocumentRepository documents;
    private final InventoryTransferRepository transfers;

    public EquipmentQueryService(ProductSerialRepository units, BranchScope scope, SerialQueryService serials,
                                 CategoryAttributes attributes, SaleableProducts products, BranchDirectory branchDirectory,
                                 UserDirectory users, InventoryDocumentRepository documents,
                                 InventoryTransferRepository transfers) {
        this.units = units;
        this.scope = scope;
        this.serials = serials;
        this.attributes = attributes;
        this.products = products;
        this.branchDirectory = branchDirectory;
        this.users = users;
        this.documents = documents;
        this.transfers = transfers;
    }

    /**
     * The equipment of the branches the node sees. {@code search} matches part of the serial number or the external code.
     */
    public Page<EquipmentView> list(String search, Long productId, Long categoryId, SerialStatus status, Long branchId,
                                    Pageable pageable) {
        InventorySorting.requireSortable(pageable.getSort(), EQUIPMENT_SORT);
        Long visible = visibleBranch(branchId);
        if (visible == NOTHING) {
            return Page.empty(pageable);
        }
        Specification<ProductSerial> spec = ProductSerialSpecifications.equipment(search, productId, categoryId, status,
                visible);
        InventoryNames names = names();
        return units.findAll(spec, pageable).map(unit -> EquipmentView.of(unit, names));
    }

    /**
     * One unit with its movements and the characteristic template of its category.
     *
     * @throws ResourceNotFoundException when the unit does not exist or its branch is not visible to this node
     */
    public EquipmentDetailView get(long id) {
        ProductSerial unit = units.findById(id).orElseThrow(() -> new ResourceNotFoundException("equipment", id));
        Long visible = visibleBranch(null);
        if (visible == NOTHING || (visible != null && visible != unit.getBranchId())) {
            throw new ResourceNotFoundException("equipment", id);
        }
        SerialTraceView trace = serials.trace(unit.getSerialNumber(), unit.getProductId()).stream()
                .filter(view -> view.id() == id).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("equipment", id));
        InventoryNames names = names();
        List<AttributeDefinition> definitions = attributes.forCategory(unit.getCategoryId());
        EquipmentView view = EquipmentView.of(unit, names);
        return new EquipmentDetailView(view.id(), view.productId(), view.productSku(), view.productName(),
                view.categoryId(), view.serialNumber(), view.externalCode(), view.attributes(), view.status(),
                view.branchId(), view.branchName(), view.receivedAt(), view.soldAt(), trace.movements(), definitions);
    }

    /**
     * The branch a read is limited to: a store sees its own branch only; the central node sees every branch unless one is
     * requested. Returns {@link #NOTHING} when the request asks for a branch this node does not see.
     */
    private Long visibleBranch(Long requested) {
        if (scope.allBranches()) {
            return requested;
        }
        var own = scope.nodeBranchId();
        if (own.isEmpty() || (requested != null && !requested.equals(own.get()))) {
            return NOTHING;
        }
        return own.get();
    }

    private InventoryNames names() {
        return new InventoryNames(branchDirectory, users, products, documents, transfers);
    }
}
