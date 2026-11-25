package com.medinasystems.pos.shared;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The rules of the serial numbers of products that track them. Shared by the inventory and the sales modules: a serial
 * number identifies one physical unit, and a document lists one serial number per unit of a serial product.
 */
public final class SerialNumbers {

    /** Longest serial number that the database keeps. */
    public static final int MAX_LENGTH = 100;

    private SerialNumbers() {
    }

    /**
     * The serial numbers of a line, without surrounding spaces and in the order given. Null means none.
     *
     * @throws BusinessRuleException SERIALS_REQUIRED for a blank serial, DUPLICATE_SERIAL when a serial appears twice
     */
    public static List<String> normalize(List<String> numbers) {
        if (numbers == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>(numbers.size());
        Set<String> seen = new HashSet<>();
        for (String number : numbers) {
            String value = number == null ? "" : number.trim();
            if (value.isEmpty()) {
                throw new BusinessRuleException("SERIALS_REQUIRED", "Each unit needs a serial number");
            }
            if (!seen.add(value)) {
                throw new BusinessRuleException("DUPLICATE_SERIAL", "The serial number is repeated", value);
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    /**
     * Checks that a quantity of a serial product is whole and that it has one serial number per unit.
     *
     * @param quantity the quantity, with or without sign
     * @param serials  the normalized serial numbers of the line
     * @throws BusinessRuleException FRACTIONAL_SERIAL_QUANTITY, SERIALS_REQUIRED or SERIAL_COUNT_MISMATCH
     */
    public static void requireOnePerUnit(BigDecimal quantity, List<String> serials) {
        BigDecimal units = quantity.abs();
        if (units.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException("FRACTIONAL_SERIAL_QUANTITY",
                    "A product with serial numbers is counted in whole units");
        }
        if (serials.isEmpty()) {
            throw new BusinessRuleException("SERIALS_REQUIRED", "The product needs serial numbers");
        }
        if (units.compareTo(BigDecimal.valueOf(serials.size())) != 0) {
            throw new BusinessRuleException("SERIAL_COUNT_MISMATCH",
                    "Each unit needs one serial number: the quantity is {0} and there are {1} serial numbers",
                    units.toBigInteger().toString(), serials.size());
        }
    }
}
