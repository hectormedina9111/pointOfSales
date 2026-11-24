package com.medinasystems.pos.pricing.infrastructure;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.pricing.domain.PriceList;

/** Filters for the price list search. A null filter does not restrict the result. */
public final class PriceListSpecifications {

    private PriceListSpecifications() {
    }

    public static Specification<PriceList> matching(Long countryId, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (countryId != null) {
                predicates.add(cb.equal(root.get("countryId"), countryId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
