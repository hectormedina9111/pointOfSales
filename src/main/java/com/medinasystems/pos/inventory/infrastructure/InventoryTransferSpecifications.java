package com.medinasystems.pos.inventory.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.inventory.domain.InventoryTransfer;
import com.medinasystems.pos.inventory.domain.TransferDirection;
import com.medinasystems.pos.inventory.domain.TransferStatus;

/** Filters of the transfer list. A null filter does not restrict the result. */
public final class InventoryTransferSpecifications {

    private InventoryTransferSpecifications() {
    }

    /**
     * @param branchId  the branch the list is about; without it, {@code direction} is ignored and every transfer matches
     * @param direction with a branch: only the transfers it sent or only those it receives; without one, both sides
     * @param search    part of the transfer number, without regard to case
     * @param from      transfers sent at or after this instant
     * @param to        transfers sent at or before this instant
     */
    public static Specification<InventoryTransfer> matching(Long branchId, TransferDirection direction,
                                                            TransferStatus status, String search, Instant from,
                                                            Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                Predicate origin = cb.equal(root.get("originBranchId"), branchId);
                Predicate destination = cb.equal(root.get("destinationBranchId"), branchId);
                if (direction == TransferDirection.OUTGOING) {
                    predicates.add(origin);
                } else if (direction == TransferDirection.INCOMING) {
                    predicates.add(destination);
                } else {
                    predicates.add(cb.or(origin, destination));
                }
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + InventoryItemSpecifications.escapeLike(search.trim().toLowerCase(Locale.ROOT))
                        + "%";
                predicates.add(cb.like(cb.lower(root.<String>get("number")), pattern, '\\'));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("sentAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("sentAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
