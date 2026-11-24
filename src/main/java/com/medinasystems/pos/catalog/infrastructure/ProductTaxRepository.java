package com.medinasystems.pos.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.catalog.domain.ProductTax;

public interface ProductTaxRepository extends JpaRepository<ProductTax, Long> {

    boolean existsByTaxId(Long taxId);
}
