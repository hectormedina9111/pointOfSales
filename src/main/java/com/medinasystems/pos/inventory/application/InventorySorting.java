package com.medinasystems.pos.inventory.application;

import java.util.Set;

import org.springframework.data.domain.Sort;

import com.medinasystems.pos.shared.BusinessRuleException;

/** Only the sort fields a list offers reach the database; any other field is refused, never ignored. */
final class InventorySorting {

    private InventorySorting() {
    }

    /** @throws BusinessRuleException with code {@code INVALID_SORT} when the sort names a field outside {@code allowed} */
    static void requireSortable(Sort sort, Set<String> allowed) {
        for (Sort.Order order : sort) {
            if (!allowed.contains(order.getProperty())) {
                throw new BusinessRuleException("INVALID_SORT", "The list cannot be sorted by this field");
            }
        }
    }
}
