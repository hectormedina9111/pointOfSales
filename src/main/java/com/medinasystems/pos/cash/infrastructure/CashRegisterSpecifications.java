package com.medinasystems.pos.cash.infrastructure;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.cash.domain.CashRegister;

/** Filters for the cash register search. A null filter does not restrict the result. */
public final class CashRegisterSpecifications {

    private CashRegisterSpecifications() {
    }

    public static Specification<CashRegister> matching(Long branchId, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
