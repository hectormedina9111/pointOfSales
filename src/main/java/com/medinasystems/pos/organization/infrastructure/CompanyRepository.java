package com.medinasystems.pos.organization.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.organization.domain.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    /** The single company of this installation, or empty when it is not configured yet. */
    Optional<Company> findFirstByOrderByIdAsc();

    boolean existsByTaxId(String taxId);

    boolean existsByTaxIdAndIdNot(String taxId, Long id);
}
