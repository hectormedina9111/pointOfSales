package com.medinasystems.pos.catalog.infrastructure;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.medinasystems.pos.catalog.domain.Category;

/** Filters for the category list. A null filter does not restrict the result. */
public final class CategorySpecifications {

    private CategorySpecifications() {
    }

    public static Specification<Category> matching(Long parentId, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (parentId != null) {
                predicates.add(cb.equal(root.get("parent").get("id"), parentId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
