package com.medinasystems.pos.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.catalog.domain.UnitOfMeasure;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, Long> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
