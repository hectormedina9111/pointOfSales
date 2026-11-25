package com.medinasystems.pos.customer.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.customer.domain.Customer;

/** Filters for the customer search. A null filter does not restrict the result. */
public final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    /**
     * {@code search} matches part of the display name, the document number or the external code, without regard to case.
     * The LIKE wildcards in {@code search} are escaped so they match literally.
     */
    public static Specification<Customer> matching(String search, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("displayName")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("documentNumber")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("externalCode")), pattern, '\\')));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
