package com.medinasystems.pos.pricing.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.pricing.application.PriceListData;
import com.medinasystems.pos.pricing.application.PriceListItemData;
import com.medinasystems.pos.pricing.application.PriceListItemView;
import com.medinasystems.pos.pricing.application.PriceListService;
import com.medinasystems.pos.pricing.application.PriceListView;
import com.medinasystems.pos.pricing.domain.PricingMethod;
import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.shared.web.PageResponse;

/**
 * Price lists and their prices per product. Items are deleted for real (DELETE returns 204), unlike
 * lists and products, which are deactivated.
 */
@RestController
@RequestMapping("/api/v1/price-lists")
public class PriceListController {

    private final PriceListService priceLists;

    public PriceListController(PriceListService priceLists) {
        this.priceLists = priceLists;
    }

    @GetMapping
    public PageResponse<PriceListView> list(@RequestParam(required = false) Long countryId,
                                            @RequestParam(required = false) Boolean active,
                                            @PageableDefault(sort = "code") Pageable pageable) {
        return PageResponse.from(priceLists.list(countryId, active, pageable));
    }

    @GetMapping("/{id}")
    public PriceListView get(@PathVariable Long id) {
        return priceLists.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CentralOnly
    public PriceListView create(@Valid @RequestBody PriceListRequest request) {
        return priceLists.create(request.toData());
    }

    @PutMapping("/{id}")
    @CentralOnly
    public PriceListView update(@PathVariable Long id, @Valid @RequestBody PriceListRequest request) {
        return priceLists.update(id, request.toData());
    }

    @GetMapping("/{id}/items")
    public PageResponse<PriceListItemView> listItems(@PathVariable Long id,
                                                     @RequestParam(required = false) Long productId,
                                                     @PageableDefault(sort = {"productId", "minQuantity"})
                                                     Pageable pageable) {
        return PageResponse.from(priceLists.listItems(id, productId, pageable));
    }

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @CentralOnly
    public PriceListItemView addItem(@PathVariable Long id, @Valid @RequestBody PriceListItemRequest request) {
        return priceLists.addItem(id, request.toData());
    }

    @PutMapping("/{id}/items/{itemId}")
    @CentralOnly
    public PriceListItemView updateItem(@PathVariable Long id, @PathVariable Long itemId,
                                        @Valid @RequestBody PriceListItemRequest request) {
        return priceLists.updateItem(id, itemId, request.toData());
    }

    /** Deletes the price for real: an item is not a catalog entry. The audit log keeps its old price. */
    @DeleteMapping("/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @CentralOnly
    public void deleteItem(@PathVariable Long id, @PathVariable Long itemId) {
        priceLists.deleteItem(id, itemId);
    }

    /** Omitted flags take their defaults: priority 0 and active. The domain checks the rest. */
    public record PriceListRequest(@NotBlank @Size(max = 30) String code,
                                   @NotBlank @Size(max = 100) String name,
                                   @NotNull Long countryId,
                                   @NotNull PricingMethod pricingMethod,
                                   BigDecimal percent,
                                   LocalDate validFrom,
                                   LocalDate validTo,
                                   Short priority,
                                   Boolean active) {

        PriceListData toData() {
            return new PriceListData(code, name, countryId, pricingMethod, percent, validFrom, validTo,
                    priority == null ? (short) 0 : priority, active == null || active);
        }
    }

    /** The minimum quantity defaults to 1 when omitted. */
    public record PriceListItemRequest(@NotNull Long productId,
                                       BigDecimal minQuantity,
                                       @NotNull BigDecimal price) {

        PriceListItemData toData() {
            return new PriceListItemData(productId, minQuantity == null ? BigDecimal.ONE : minQuantity, price);
        }
    }
}
