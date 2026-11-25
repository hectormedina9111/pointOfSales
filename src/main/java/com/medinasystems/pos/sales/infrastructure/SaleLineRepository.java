package com.medinasystems.pos.sales.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.sales.domain.SaleLine;
import com.medinasystems.pos.sales.domain.SaleType;

public interface SaleLineRepository extends JpaRepository<SaleLine, Long> {

    /**
     * The amount refunded per original line, by the returns of one sale. Each row is {@code [originalLineId, sum]}.
     */
    @Query("select l.originalLineId, sum(l.total) from SaleLine l join l.sale s "
            + "where s.type = :type and s.originalSaleId = :saleId group by l.originalLineId")
    List<Object[]> sumTotalsByOriginalLine(@Param("saleId") long saleId, @Param("type") SaleType type);
}
