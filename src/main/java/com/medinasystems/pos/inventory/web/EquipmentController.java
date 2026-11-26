package com.medinasystems.pos.inventory.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.inventory.application.EquipmentLoadData;
import com.medinasystems.pos.inventory.application.EquipmentRules;
import com.medinasystems.pos.inventory.application.EquipmentService;
import com.medinasystems.pos.inventory.application.EquipmentView;
import com.medinasystems.pos.inventory.application.EquipmentUnit;
import com.medinasystems.pos.inventory.application.InventoryDocumentService;
import com.medinasystems.pos.inventory.application.InventoryDocumentView;
import com.medinasystems.pos.shared.SerialNumbers;

/** Equipment: the units of products that track serial numbers, each with its characteristics and external code. */
@RestController
@RequestMapping("/api/v1/inventory")
public class EquipmentController {

    private final InventoryDocumentService documents;
    private final EquipmentService equipment;

    public EquipmentController(InventoryDocumentService documents, EquipmentService equipment) {
        this.documents = documents;
        this.equipment = equipment;
    }

    /**
     * Loads equipment: the entry of the units, one per serial number. Returns the entry document that the load created. A key
     * already used returns the document that key created.
     */
    @PostMapping("/equipment-loads")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryDocumentView load(@Valid @RequestBody EquipmentLoadRequest request) {
        return documents.postEquipmentLoad(request.toData());
    }

    /**
     * Edits the external code and the characteristics of one unit. The node must operate the branch where the unit is; the
     * characteristics are checked against the template of the product's category.
     */
    @PutMapping("/equipment/{id}")
    public EquipmentView update(@PathVariable long id, @Valid @RequestBody EquipmentUpdateRequest request) {
        return equipment.update(id, request.externalCode(), request.attributes() == null ? Map.of()
                : request.attributes());
    }

    /** The external code is optional: a blank code removes it. */
    public record EquipmentUpdateRequest(@Size(max = EquipmentRules.MAX_EXTERNAL_CODE_LENGTH) String externalCode,
                                         Map<String, Object> attributes) {
    }

    /** The branch is optional on a store (its own) and required on the central node. The unit cost is optional. */
    public record EquipmentLoadRequest(Long branchId,
                                       @NotNull Long productId,
                                       @Digits(integer = 10, fraction = 4) BigDecimal unitCost,
                                       @Size(max = 500) String notes,
                                       @Size(max = 64) String idempotencyKey,
                                       @NotEmpty @Size(max = 500) List<@Valid UnitRequest> equipment) {

        EquipmentLoadData toData() {
            return new EquipmentLoadData(branchId, productId, unitCost, notes, idempotencyKey,
                    equipment.stream().map(UnitRequest::toData).toList());
        }
    }

    /** One unit: its serial number, its external code (optional) and its characteristics by code. */
    public record UnitRequest(@NotBlank @Size(max = SerialNumbers.MAX_LENGTH) String serialNumber,
                              @Size(max = EquipmentRules.MAX_EXTERNAL_CODE_LENGTH) String externalCode,
                              Map<String, Object> attributes) {

        EquipmentUnit toData() {
            return new EquipmentUnit(serialNumber, externalCode, attributes == null ? Map.of() : attributes);
        }
    }
}
