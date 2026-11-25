package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;

import com.medinasystems.pos.sales.domain.CalculatedLine;
import com.medinasystems.pos.sales.domain.DiscountType;
import com.medinasystems.pos.sales.domain.PricedLine;
import com.medinasystems.pos.sales.domain.SaleLine;

/**
 * A line of a quote or of a sale. {@code discountAmount} includes the line's share of the sale discount;
 * {@code subtotal + taxAmount = total}.
 */
public record SaleLineView(int lineNumber, long productId, String sku, String description, BigDecimal quantity,
                           BigDecimal unitPrice, String taxCode, BigDecimal taxRate, Long priceListId,
                           DiscountType discountType, BigDecimal discountValue, BigDecimal discountAmount,
                           BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total) {

    public static SaleLineView from(CalculatedLine calculated) {
        PricedLine line = calculated.line();
        return new SaleLineView(calculated.lineNumber(), line.productId(), line.sku(), line.description(),
                line.quantity(), line.unitPrice(), line.taxCode(), line.taxRate(), line.priceListId(),
                line.discount() == null ? null : line.discount().type(),
                line.discount() == null ? null : line.discount().value(),
                calculated.discountAmount(), calculated.subtotal(), calculated.taxAmount(), calculated.total());
    }

    public static SaleLineView from(SaleLine line) {
        return new SaleLineView(line.getLineNumber().intValue(), line.getProductId(), line.getSku(),
                line.getDescription(), line.getQuantity(), line.getUnitPrice(), line.getTaxCode(), line.getTaxRate(),
                line.getPriceListId(), line.getDiscountType(), line.getDiscountValue(), line.getDiscountAmount(),
                line.getSubtotal(), line.getTaxAmount(), line.getTotal());
    }
}
