package com.medinasystems.pos.pricing.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.medinasystems.pos.pricing.domain.PriceList;

public interface PriceListRepository extends JpaRepository<PriceList, Long>, JpaSpecificationExecutor<PriceList> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
