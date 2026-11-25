package com.medinasystems.pos.cash.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.cash.domain.CashSession;
import com.medinasystems.pos.cash.domain.CashSessionStatus;

/**
 * Filters for the cash session search. A null filter does not restrict the result. {@code registerIds}, when not
 * null, limits the sessions to those registers: it is the visibility rule of the node, not a user filter.
 */
public final class CashSessionSpecifications {

    private CashSessionSpecifications() {
    }

    public static Specification<CashSession> matching(List<Long> registerIds, Long cashRegisterId, Long userId,
                                                      CashSessionStatus status, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (registerIds != null) {
                predicates.add(root.get("cashRegisterId").in(registerIds));
            }
            if (cashRegisterId != null) {
                predicates.add(cb.equal(root.get("cashRegisterId"), cashRegisterId));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("openedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("openedAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
