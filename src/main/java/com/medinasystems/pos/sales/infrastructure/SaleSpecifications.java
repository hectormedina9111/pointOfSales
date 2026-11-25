package com.medinasystems.pos.sales.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.sales.application.SaleFilter;
import com.medinasystems.pos.sales.domain.Sale;

/**
 * Filters for the sales list, applied in the database so the page is built there. A null filter does not restrict
 * the result. {@code branchId} and {@code cashierId} are the visibility rules of the node, already set in the filter.
 * {@code from} and {@code to} bound the creation time of the sale.
 */
public final class SaleSpecifications {

    /** Backslash escapes the wildcards of a LIKE pattern. */
    private static final char LIKE_ESCAPE = '\\';

    private SaleSpecifications() {
    }

    public static Specification<Sale> matching(SaleFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.branchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), filter.branchId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.orderType() != null) {
                predicates.add(cb.equal(root.get("orderType"), filter.orderType()));
            }
            if (filter.cashSessionId() != null) {
                predicates.add(cb.equal(root.get("cashSessionId"), filter.cashSessionId()));
            }
            if (filter.cashRegisterId() != null) {
                predicates.add(cb.equal(root.get("cashRegisterId"), filter.cashRegisterId()));
            }
            if (filter.cashierId() != null) {
                predicates.add(cb.equal(root.get("cashierId"), filter.cashierId()));
            }
            if (filter.customerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), filter.customerId()));
            }
            if (filter.search() != null) {
                predicates.add(matchesSearch(root, cb, filter.search()));
            }
            if (filter.minTotal() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("total"), filter.minTotal()));
            }
            if (filter.maxTotal() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("total"), filter.maxTotal()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * The receipt number contains the text, ignoring case. When the text is all digits it also matches the internal
     * number exactly. The digit limit keeps the value inside a Long.
     */
    private static Predicate matchesSearch(Root<Sale> root, CriteriaBuilder cb, String text) {
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        Predicate byReceipt = cb.like(cb.lower(root.<String>get("receiptNumber")), pattern, LIKE_ESCAPE);
        if (!text.matches("\\d{1,18}")) {
            return byReceipt;
        }
        return cb.or(byReceipt, cb.equal(root.get("internalNumber"), Long.valueOf(text)));
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
