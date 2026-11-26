package com.medinasystems.pos.sales.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.SaleableProduct;
import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.sales.SaleSerials;
import com.medinasystems.pos.sales.domain.SaleStatus;
import com.medinasystems.pos.sales.domain.SaleType;
import com.medinasystems.pos.sales.infrastructure.SaleLineSerialRepository;

/** Answers {@link SaleSerials} from the sale lines of this node. */
@Component
public class DefaultSaleSerials implements SaleSerials {

    /** The statuses of a sale that was charged: its units may have been sold. Holds are not sales yet. */
    static final Set<SaleStatus> CHARGED = Set.of(SaleStatus.PAID, SaleStatus.PARTIALLY_RETURNED, SaleStatus.RETURNED);

    private final SaleLineSerialRepository serials;
    private final SaleableProducts products;

    public DefaultSaleSerials(SaleLineSerialRepository serials, SaleableProducts products) {
        this.serials = serials;
        this.products = products;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManualSerialSale> manualSerialSales(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return List.of();
        }
        Map<Long, Boolean> manual = new HashMap<>();
        List<ManualSerialSale> found = new ArrayList<>();
        for (Object[] row : serials.findSaleRows(serialNumber.trim(), SaleType.SALE, CHARGED)) {
            long productId = (Long) row[0];
            boolean isManual = manual.computeIfAbsent(productId,
                    id -> products.find(id).map(SaleableProduct::isSerialManual).orElse(false));
            if (isManual) {
                found.add(new ManualSerialSale(productId, (Long) row[1], (String) row[2], (Long) row[3],
                        (Instant) row[4], (Boolean) row[5]));
            }
        }
        return found;
    }
}
