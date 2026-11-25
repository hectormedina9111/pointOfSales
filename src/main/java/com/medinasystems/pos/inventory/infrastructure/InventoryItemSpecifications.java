package com.medinasystems.pos.inventory.infrastructure;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.inventory.domain.InventoryItem;

/** Filters of the stock list. A null filter does not restrict the result; the filters run in the database. */
public final class InventoryItemSpecifications {

    private InventoryItemSpecifications() {
    }

    /**
     * @param branchId   only this branch, when set
     * @param search     part of the sku or of the name, without regard to case; LIKE wildcards match literally
     * @param categoryId only this category, when set
     * @param belowMin   true for products below their minimum, false for the others; null for all
     */
    public static Specification<InventoryItem> matching(Long branchId, String search, Long categoryId,
                                                        Boolean belowMin) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("productSku")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("productName")), pattern, '\\')));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (belowMin != null) {
                Predicate below = cb.and(
                        cb.greaterThan(root.<BigDecimal>get("minStock"), BigDecimal.ZERO),
                        cb.lessThan(root.<BigDecimal>get("quantity"), root.<BigDecimal>get("minStock")));
                predicates.add(belowMin ? below : cb.not(below));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
