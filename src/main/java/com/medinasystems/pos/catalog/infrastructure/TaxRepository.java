package com.medinasystems.pos.catalog.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.catalog.domain.Tax;

public interface TaxRepository extends JpaRepository<Tax, Long> {

    Page<Tax> findByCountryId(Long countryId, Pageable pageable);

    boolean existsByCountryIdAndCode(Long countryId, String code);

    boolean existsByCountryIdAndCodeAndIdNot(Long countryId, String code, Long id);
}
