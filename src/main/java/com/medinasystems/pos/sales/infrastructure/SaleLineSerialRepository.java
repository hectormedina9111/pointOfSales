package com.medinasystems.pos.sales.infrastructure;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.sales.domain.SaleLineSerial;
import com.medinasystems.pos.sales.domain.SaleStatus;
import com.medinasystems.pos.sales.domain.SaleType;

public interface SaleLineSerialRepository extends JpaRepository<SaleLineSerial, Long> {

    /**
     * The serial numbers of this product that a charged sale sold and that did not come back yet. Returns and holds are
     * left out.
     */
    @Query("select s.serialNumber from SaleLineSerial s join s.saleLine l join l.sale sale "
            + "where l.productId = :productId and s.serialNumber in :numbers and s.returned = false "
            + "and sale.type = :type and sale.status in :statuses")
    List<String> findSoldNumbers(@Param("productId") long productId, @Param("numbers") Collection<String> numbers,
                                 @Param("type") SaleType type, @Param("statuses") Collection<SaleStatus> statuses);

    /**
     * Every sale line serial with this number, without regard to case, of the charged sales. Each row is
     * {@code [productId, saleId, receiptNumber, branchId, paidAt, returned]}, oldest sale first.
     */
    @Query("select l.productId, sale.id, sale.receiptNumber, sale.branchId, sale.paidAt, s.returned "
            + "from SaleLineSerial s join s.saleLine l join l.sale sale "
            + "where lower(s.serialNumber) = lower(:serialNumber) and sale.type = :type and sale.status in :statuses "
            + "order by sale.paidAt asc, sale.id asc")
    List<Object[]> findSaleRows(@Param("serialNumber") String serialNumber, @Param("type") SaleType type,
                                @Param("statuses") Collection<SaleStatus> statuses);
}
