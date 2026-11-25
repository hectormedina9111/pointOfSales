package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;

/**
 * What a sale can use of a product in a branch. {@code tracksInventory} is false for services and for products that do not
 * track stock, and then {@code quantity} is zero. {@code allowNegativeStock} tells whether the branch sells without stock.
 */
public record ProductAvailabilityView(long productId, boolean tracksInventory, BigDecimal quantity,
                                      boolean allowNegativeStock) {
}
