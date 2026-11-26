package com.medinasystems.pos.inventory.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.SaleableProduct;
import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.inventory.domain.ProductSerial;
import com.medinasystems.pos.inventory.infrastructure.InventoryDocumentRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryTransferRepository;
import com.medinasystems.pos.inventory.infrastructure.ProductSerialRepository;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.shared.ResourceNotFoundException;
import com.medinasystems.pos.sync.Outbox;

/**
 * Edits the external code and the characteristics of one unit of equipment. Only the node that operates the branch where the
 * unit is may edit it. A store queues the edit for the central node; the central node applies it directly.
 */
@Service
@Transactional
public class EquipmentService {

    private static final String SYSTEM_AUDITOR = "system";

    private final ProductSerialRepository units;
    private final InventoryBranches branches;
    private final SaleableProducts products;
    private final EquipmentRules rules;
    private final NodeProperties node;
    private final Outbox outbox;
    private final ApplicationEventPublisher events;
    private final AuditorAware<String> auditor;
    private final Clock clock;
    private final BranchDirectory branchDirectory;
    private final UserDirectory users;
    private final InventoryDocumentRepository documents;
    private final InventoryTransferRepository transfers;

    public EquipmentService(ProductSerialRepository units, InventoryBranches branches, SaleableProducts products,
                            EquipmentRules rules, NodeProperties node, Outbox outbox, ApplicationEventPublisher events,
                            AuditorAware<String> auditor, Clock clock, BranchDirectory branchDirectory, UserDirectory users,
                            InventoryDocumentRepository documents, InventoryTransferRepository transfers) {
        this.units = units;
        this.branches = branches;
        this.products = products;
        this.rules = rules;
        this.node = node;
        this.outbox = outbox;
        this.events = events;
        this.auditor = auditor;
        this.clock = clock;
        this.branchDirectory = branchDirectory;
        this.users = users;
        this.documents = documents;
        this.transfers = transfers;
    }

    /**
     * Replaces the external code and the characteristics of a unit. The characteristics are checked against the template of the
     * product's category; the code must not belong to another unit.
     *
     * @throws BusinessRuleException INVALID_ATTRIBUTE_VALUE and the other characteristic rules, DUPLICATE_EQUIPMENT_EXTERNAL_CODE,
     *                               INVENTORY_BRANCH_NOT_OPERATED
     */
    public EquipmentView update(long id, String externalCode, Map<String, Object> attributes) {
        ProductSerial unit = units.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("equipment", id));
        branches.operated(unit.getBranchId());
        SaleableProduct product = products.find(unit.getProductId())
                .orElseThrow(() -> new BusinessRuleException("PRODUCT_NOT_FOUND", "The product does not exist"));
        EquipmentUnit checked = rules.normalize(product, new EquipmentUnit(unit.getSerialNumber(), externalCode,
                attributes));
        Map<String, EquipmentUnit> one = new LinkedHashMap<>();
        one.put(checked.serialNumber(), checked);
        rules.requireFreeCodes(product.id(), one);

        Map<String, Object> before = snapshot(unit);
        unit.applyEquipment(checked.externalCode(), checked.attributes());
        Instant now = clock.instant();
        String by = auditor.getCurrentAuditor().orElse(SYSTEM_AUDITOR);
        if (node.isStore()) {
            outbox.enqueue(EquipmentUpdatedDocument.EVENT_TYPE, EquipmentUpdatedDocument.of(unit, unit.getBranchId(),
                    now, by));
        }
        events.publishEvent(AuditEvent.updated("EQUIPMENT_UPDATED", "product_serial", id, before, snapshot(unit)));
        return EquipmentView.of(unit, names());
    }

    private static Map<String, Object> snapshot(ProductSerial unit) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("externalCode", unit.getExternalCode());
        values.put("attributes", unit.getAttributes());
        return values;
    }

    private InventoryNames names() {
        return new InventoryNames(branchDirectory, users, products, documents, transfers);
    }
}
