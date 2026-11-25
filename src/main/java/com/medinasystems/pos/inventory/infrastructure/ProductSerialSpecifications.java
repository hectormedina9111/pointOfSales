package com.medinasystems.pos.inventory.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.inventory.domain.ProductSerial;
import com.medinasystems.pos.inventory.domain.SerialStatus;

/** The filters of the serial units, applied in the database. */
public final class ProductSerialSpecifications {

    private ProductSerialSpecifications() {
    }

    /**
     * The units of one product.
     *
     * @param branchId null for every branch the node sees
     * @param status   null for every status
     * @param search   part of the serial number, without regard to case; LIKE wildcards match literally
     */
    public static Specification<ProductSerial> forProduct(long productId, Long branchId, SerialStatus status,
                                                          String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("productId"), productId));
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + InventoryItemSpecifications.escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.<String>get("serialNumber")), pattern, '\\'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** The units with exactly this serial number, without regard to case, optionally of one product and branch. */
    public static Specification<ProductSerial> trace(String serialNumber, Long productId, Long branchId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(cb.lower(root.<String>get("serialNumber")), serialNumber.toLowerCase(Locale.ROOT)));
            if (productId != null) {
                predicates.add(cb.equal(root.get("productId"), productId));
            }
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
