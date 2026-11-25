package com.medinasystems.pos.inventory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * The arithmetic of stock: the weighted average cost and the precision of quantities. Costs keep 4 decimals, as the
 * database does; quantities keep 3, or none for units that do not allow decimals.
 */
public final class StockCalculator {

    /** Decimals of a unit cost and of an average cost (NUMERIC(14,4)). */
    public static final int COST_SCALE = 4;
    /** Decimals of a quantity (NUMERIC(12,3)). */
    public static final int QUANTITY_SCALE = 3;

    private StockCalculator() {
    }

    /**
     * The average cost after an entry: {@code (quantity x average + entered x cost) / (quantity + entered)}. When the stock
     * was zero or negative, the average becomes the cost of the entry.
     *
     * @param quantityBefore the stock before the entry
     * @param averageBefore  the average cost before the entry
     * @param entered        the positive quantity that enters
     * @param unitCost       the cost of the entered quantity
     */
    public static BigDecimal averageAfterEntry(BigDecimal quantityBefore, BigDecimal averageBefore,
                                               BigDecimal entered, BigDecimal unitCost) {
        if (quantityBefore.signum() <= 0) {
            return unitCost.setScale(COST_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal total = quantityBefore.multiply(averageBefore).add(entered.multiply(unitCost));
        BigDecimal quantityAfter = quantityBefore.add(entered);
        return total.divide(quantityAfter, COST_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Checks that a quantity has no more decimals than its unit allows, and returns it with that scale.
     *
     * @throws BusinessRuleException with code {@code QUANTITY_PRECISION_NOT_ALLOWED} when it has too many decimals
     */
    public static BigDecimal quantity(BigDecimal quantity, boolean allowsDecimals) {
        int maxScale = allowsDecimals ? QUANTITY_SCALE : 0;
        if (quantity.stripTrailingZeros().scale() > maxScale) {
            throw new BusinessRuleException("QUANTITY_PRECISION_NOT_ALLOWED",
                    "The quantity has more decimals than its unit allows");
        }
        return quantity.setScale(maxScale, RoundingMode.UNNECESSARY);
    }

    /** A cost with the scale of the database. Costs are rounded HALF_UP, so no value is lost by the storage. */
    public static BigDecimal cost(BigDecimal cost) {
        return cost.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }
}
