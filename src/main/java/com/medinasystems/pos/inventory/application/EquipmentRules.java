package com.medinasystems.pos.inventory.application;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.medinasystems.pos.catalog.CategoryAttributes;
import com.medinasystems.pos.catalog.SaleableProduct;
import com.medinasystems.pos.inventory.domain.ProductSerial;
import com.medinasystems.pos.inventory.infrastructure.ProductSerialRepository;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.SerialNumbers;

/**
 * The rules of the equipment that a load or an edit writes: serial numbers (one per unit, no repeats), external codes
 * (optional, unique when they have a value) and characteristics checked against the template of the category.
 */
@Component
public class EquipmentRules {

    /** Longest external code that the database keeps. */
    public static final int MAX_EXTERNAL_CODE_LENGTH = 50;

    private final ProductSerialRepository serialUnits;
    private final CategoryAttributes attributes;

    public EquipmentRules(ProductSerialRepository serialUnits, CategoryAttributes attributes) {
        this.serialUnits = serialUnits;
        this.attributes = attributes;
    }

    /**
     * Checks the units of a load, keyed by their normalized serial number.
     *
     * @throws BusinessRuleException SERIALS_REQUIRED, DUPLICATE_SERIAL, DUPLICATE_EQUIPMENT_EXTERNAL_CODE, or a characteristic rule
     */
    public Map<String, EquipmentUnit> check(SaleableProduct product, List<EquipmentUnit> units) {
        List<String> serials = SerialNumbers.normalize(units == null ? null
                : units.stream().map(EquipmentUnit::serialNumber).toList());
        if (serials.isEmpty()) {
            throw new BusinessRuleException("SERIALS_REQUIRED", "Each unit needs a serial number");
        }
        Map<String, EquipmentUnit> result = new LinkedHashMap<>();
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < serials.size(); i++) {
            EquipmentUnit unit = units.get(i);
            String code = externalCode(unit.externalCode());
            if (code != null && !codes.add(code)) {
                throw duplicateCode(code);
            }
            result.put(serials.get(i), normalize(product, new EquipmentUnit(serials.get(i), code, unit.attributes())));
        }
        requireFreeCodes(product.id(), result);
        return result;
    }

    /** One unit checked alone, for an edit. The unit keeps its serial number. */
    public EquipmentUnit normalize(SaleableProduct product, EquipmentUnit unit) {
        Map<String, Object> values = attributes.validate(product.categoryId(), unit.attributes());
        return new EquipmentUnit(unit.serialNumber(), externalCode(unit.externalCode()), values);
    }

    /**
     * The external codes of the units must not belong to another unit. A unit of the same product and serial number is the
     * same equipment, so it may keep its code.
     *
     * @throws BusinessRuleException DUPLICATE_EQUIPMENT_EXTERNAL_CODE
     */
    public void requireFreeCodes(long productId, Map<String, EquipmentUnit> units) {
        Set<String> codes = new HashSet<>();
        units.values().forEach(unit -> {
            if (unit.externalCode() != null) {
                codes.add(unit.externalCode());
            }
        });
        if (codes.isEmpty()) {
            return;
        }
        for (ProductSerial row : serialUnits.findByExternalCodeIn(codes)) {
            boolean same = row.getProductId() == productId && units.containsKey(row.getSerialNumber());
            if (!same) {
                throw duplicateCode(row.getExternalCode());
            }
        }
    }

    /** Trims the code; a blank code means none. */
    static String externalCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String code = value.trim();
        if (code.length() > MAX_EXTERNAL_CODE_LENGTH) {
            throw new BusinessRuleException("EXTERNAL_CODE_TOO_LONG", "The external code is too long");
        }
        return code;
    }

    private static BusinessRuleException duplicateCode(String code) {
        return new BusinessRuleException("DUPLICATE_EQUIPMENT_EXTERNAL_CODE",
                "The external code {0} is already used by other equipment", code);
    }
}
