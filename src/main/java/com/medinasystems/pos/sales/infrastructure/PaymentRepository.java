package com.medinasystems.pos.sales.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.sales.domain.Payment;
import com.medinasystems.pos.sales.domain.SaleStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * The sum of the payments of the sales of one cash session, per payment method code. Each row is
     * {@code [code, sum]}.
     */
    @Query("select m.code, sum(p.amount) from Payment p join p.paymentMethod m join p.sale s "
            + "where s.cashSessionId = :cashSessionId and s.status = :status group by m.code")
    List<Object[]> sumBySessionAndStatus(@Param("cashSessionId") long cashSessionId,
                                         @Param("status") SaleStatus status);
}
