package com.medinasystems.pos.organization.infrastructure;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.organization.domain.Branch;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    Page<Branch> findByIdIn(Collection<Long> ids, Pageable pageable);
}
