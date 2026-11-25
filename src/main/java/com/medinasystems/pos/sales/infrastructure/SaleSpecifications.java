package com.medinasystems.pos.sales.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleStatus;

/**
 * Filters for the sales search. A null filter does not restrict the result. {@code branchId}, when not null, is the
 * visibility rule of the node, not a user filter. {@code from} and {@code to} bound the creation time of the sale.
 */
public final class SaleSpecifications {

    private SaleSpecifications() {
    }

    public static Specification<Sale> matching(Long branchId, SaleStatus status, Long cashSessionId, Long cashierId,
                                               Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (cashSessionId != null) {
                predicates.add(cb.equal(root.get("cashSessionId"), cashSessionId));
            }
            if (cashierId != null) {
                predicates.add(cb.equal(root.get("cashierId"), cashierId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
