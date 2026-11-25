package com.medinasystems.pos.inventory.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.domain.InventoryMovement;

/** Filters of the movement log. A null filter does not restrict the result. */
public final class InventoryMovementSpecifications {

    private InventoryMovementSpecifications() {
    }

    /** Moves in {@code [from, to]} (both ends included) of one product, branch and type; null filters are ignored. */
    public static Specification<InventoryMovement> matching(Long branchId, Long productId, StockMovementType type,
                                                            Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (productId != null) {
                predicates.add(cb.equal(root.get("productId"), productId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("movedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("movedAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
