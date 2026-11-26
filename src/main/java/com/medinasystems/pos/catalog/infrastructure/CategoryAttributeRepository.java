package com.medinasystems.pos.catalog.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.catalog.domain.CategoryAttribute;

public interface CategoryAttributeRepository extends JpaRepository<CategoryAttribute, Long> {

    /** All the characteristics of a category, active or not, in template order. */
    List<CategoryAttribute> findByCategoryIdOrderBySortOrderAscIdAsc(long categoryId);

    /** The active characteristics of a category, in template order. */
    List<CategoryAttribute> findByCategoryIdAndActiveTrueOrderBySortOrderAscIdAsc(long categoryId);

    boolean existsByCategoryIdAndCode(long categoryId, String code);
}
