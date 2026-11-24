package com.medinasystems.pos.catalog.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.catalog.domain.Product;
import com.medinasystems.pos.catalog.domain.ProductType;

/** Filters for the product search. A null filter does not restrict the result. */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /**
     * {@code q} matches part of the name without regard to case, or the exact SKU or barcode.
     * The LIKE wildcards in {@code q} are escaped so they match literally.
     */
    public static Specification<Product> matching(String q, Long categoryId, ProductType type, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String term = q.trim();
                String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("name")), pattern, '\\'),
                        cb.equal(root.get("sku"), term),
                        cb.equal(root.get("barcode"), term)));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("productType"), type));
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
