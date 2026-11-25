package com.medinasystems.pos.cash.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.medinasystems.pos.cash.domain.CashRegister;

public interface CashRegisterRepository extends JpaRepository<CashRegister, Long>, JpaSpecificationExecutor<CashRegister> {

    boolean existsByBranchIdAndCode(Long branchId, String code);

    boolean existsByBranchIdAndCodeAndIdNot(Long branchId, String code, Long id);
}
