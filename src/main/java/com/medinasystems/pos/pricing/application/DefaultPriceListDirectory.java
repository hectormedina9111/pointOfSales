package com.medinasystems.pos.pricing.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.pricing.PriceListDirectory;
import com.medinasystems.pos.pricing.infrastructure.PriceListRepository;

/** Answers {@link PriceListDirectory} from the price list table. */
@Component
public class DefaultPriceListDirectory implements PriceListDirectory {

    private final PriceListRepository lists;

    public DefaultPriceListDirectory(PriceListRepository lists) {
        this.lists = lists;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(long priceListId) {
        return lists.existsById(priceListId);
    }
}
