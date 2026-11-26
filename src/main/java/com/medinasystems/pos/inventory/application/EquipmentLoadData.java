package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * An equipment load: the entry of units of one product that tracks serial numbers, each one with its characteristics. The
 * branch is required on the central node and optional on a store. A missing unit cost uses the average or the product cost.
 */
public record EquipmentLoadData(Long branchId, long productId, BigDecimal unitCost, String notes, String idempotencyKey,
                                List<EquipmentUnit> equipment) {
}
