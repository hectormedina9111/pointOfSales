package com.medinasystems.pos.organization.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.organization.domain.Country;

public interface CountryRepository extends JpaRepository<Country, Long> {

    boolean existsByIsoCode(String isoCode);

    Optional<Country> findByIsoCode(String isoCode);

    boolean existsByIsoCodeAndIdNot(String isoCode, Long id);

    boolean existsByIdAndActiveTrue(Long id);
}
