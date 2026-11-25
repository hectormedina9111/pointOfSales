package com.medinasystems.pos.sales.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.sales.application.CreditNoteFilter;
import com.medinasystems.pos.sales.domain.CreditNote;

/** Filters for the credit note list, applied in the database. A null filter does not restrict the result. */
public final class CreditNoteSpecifications {

    /** Backslash escapes the wildcards of a LIKE pattern. */
    private static final char LIKE_ESCAPE = '\\';

    private CreditNoteSpecifications() {
    }

    public static Specification<CreditNote> matching(CreditNoteFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.customerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), filter.customerId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.branchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), filter.branchId()));
            }
            if (filter.search() != null) {
                predicates.add(matchesNumber(root, cb, filter.search()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("issuedAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("issuedAt"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate matchesNumber(Root<CreditNote> root, CriteriaBuilder cb, String text) {
        String pattern = "%" + text.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return cb.like(cb.lower(root.<String>get("number")), pattern, LIKE_ESCAPE);
    }
}
