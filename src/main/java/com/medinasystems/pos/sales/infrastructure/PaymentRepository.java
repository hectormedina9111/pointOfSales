package com.medinasystems.pos.sales.infrastructure;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.sales.domain.Payment;
import com.medinasystems.pos.sales.domain.SaleStatus;
import com.medinasystems.pos.sales.domain.SaleType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * The net amount of the payments of the documents of one cash session, per payment method code. Each row is
     * {@code [code, sum]}. The refunds of RETURN documents count negative: they leave the session.
     */
    @Query("select m.code, sum(case when s.type = :returnType then -p.amount else p.amount end) "
            + "from Payment p join p.paymentMethod m join p.sale s "
            + "where s.cashSessionId = :cashSessionId and s.status in :statuses group by m.code")
    List<Object[]> sumBySessionAndStatuses(@Param("cashSessionId") long cashSessionId,
                                           @Param("statuses") Collection<SaleStatus> statuses,
                                           @Param("returnType") SaleType returnType);

    /** The refunds given by the returns of one sale, per payment method code. Each row is {@code [code, sum]}. */
    @Query("select m.code, sum(p.amount) from Payment p join p.paymentMethod m join p.sale s "
            + "where s.type = :type and s.originalSaleId = :saleId group by m.code")
    List<Object[]> sumByMethodOfReturns(@Param("saleId") long saleId, @Param("type") SaleType type);
}
