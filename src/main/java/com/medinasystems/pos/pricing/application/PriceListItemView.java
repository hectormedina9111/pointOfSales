package com.medinasystems.pos.pricing.application;

import java.math.BigDecimal;

import com.medinasystems.pos.pricing.domain.PriceListItem;

public record PriceListItemView(Long id, Long priceListId, Long productId, BigDecimal minQuantity,
                                BigDecimal price) {

    public static PriceListItemView from(PriceListItem item) {
        return new PriceListItemView(item.getId(), item.getPriceListId(), item.getProductId(),
                item.getMinQuantity(), item.getPrice());
    }
}
