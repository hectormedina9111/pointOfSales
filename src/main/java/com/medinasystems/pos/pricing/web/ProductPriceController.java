package com.medinasystems.pos.pricing.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.pricing.application.PriceService;
import com.medinasystems.pos.pricing.application.ProductPriceView;

/** The resolved price of a product, with its taxable base and tax. Read-only. */
@RestController
@RequestMapping("/api/v1/products")
public class ProductPriceController {

    private final PriceService prices;

    public ProductPriceController(PriceService prices) {
        this.prices = prices;
    }

    @GetMapping("/{id}/price")
    public ProductPriceView resolve(@PathVariable Long id,
                                    @RequestParam(required = false) Long priceListId,
                                    @RequestParam(required = false) Long branchId,
                                    @RequestParam(defaultValue = "1") BigDecimal quantity,
                                    @RequestParam(required = false) LocalDate date) {
        return prices.resolve(id, priceListId, branchId, quantity, date);
    }
}
