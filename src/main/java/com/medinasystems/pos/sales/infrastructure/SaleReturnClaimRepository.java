package com.medinasystems.pos.sales.infrastructure;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.sales.domain.ReturnClaimStatus;
import com.medinasystems.pos.sales.domain.SaleReturnClaim;
import com.medinasystems.pos.sales.domain.SaleType;

public interface SaleReturnClaimRepository extends JpaRepository<SaleReturnClaim, Long> {

    List<SaleReturnClaim> findByClaimKeyAndSaleId(String claimKey, long saleId);

    List<SaleReturnClaim> findByClaimKeyAndStoreNodeId(String claimKey, long storeNodeId);

    /**
     * The quantity held per line of a sale: the RESERVED and CONFIRMED claims whose return is not already in this node's
     * documents. A claim whose return is here is counted by the returned quantity instead, so it is never counted twice.
     * Each row is {@code [saleLineId, sum]}.
     */
    @Query("select c.saleLineId, sum(c.quantity) from SaleReturnClaim c where c.saleId = :saleId"
            + " and c.status in :statuses and not exists (select r.id from Sale r"
            + " where r.idempotencyKey = c.claimKey and r.type = :returnType) group by c.saleLineId")
    List<Object[]> sumHeldByLine(@Param("saleId") long saleId,
                                 @Param("statuses") Collection<ReturnClaimStatus> statuses,
                                 @Param("returnType") SaleType returnType);
}
