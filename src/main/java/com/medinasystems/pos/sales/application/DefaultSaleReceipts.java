package com.medinasystems.pos.sales.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.sales.SaleReceipts;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.infrastructure.SaleRepository;

/** Receipt numbers of sales and returns, in one query for all the ids asked. */
@Service
@Transactional(readOnly = true)
public class DefaultSaleReceipts implements SaleReceipts {

    private final SaleRepository sales;

    public DefaultSaleReceipts(SaleRepository sales) {
        this.sales = sales;
    }

    @Override
    public Map<Long, String> receiptNumbers(Collection<Long> saleIds) {
        Map<Long, String> numbers = new HashMap<>();
        if (saleIds.isEmpty()) {
            return numbers;
        }
        for (Sale sale : sales.findAllById(saleIds)) {
            if (sale.getReceiptNumber() != null) {
                numbers.put(sale.getId(), sale.getReceiptNumber());
            }
        }
        return numbers;
    }
}
