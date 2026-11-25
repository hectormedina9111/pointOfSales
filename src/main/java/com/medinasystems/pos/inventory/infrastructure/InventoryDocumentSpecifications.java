package com.medinasystems.pos.inventory.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.inventory.domain.InventoryDocument;
import com.medinasystems.pos.inventory.domain.InventoryDocumentType;

/** Filters of the document list. A null filter does not restrict the result. */
public final class InventoryDocumentSpecifications {

    private InventoryDocumentSpecifications() {
    }

    /**
     * @param search part of the document number, without regard to case
     * @param from   documents posted at or after this instant
     * @param to     documents posted at or before this instant
     */
    public static Specification<InventoryDocument> matching(Long branchId, InventoryDocumentType type, String search,
                                                            Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + InventoryItemSpecifications.escapeLike(search.trim().toLowerCase(Locale.ROOT))
                        + "%";
                predicates.add(cb.like(cb.lower(root.<String>get("number")), pattern, '\\'));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("postedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("postedAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
