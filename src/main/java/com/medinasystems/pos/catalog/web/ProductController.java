package com.medinasystems.pos.catalog.web;

import java.math.BigDecimal;
import java.util.List;

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

import com.medinasystems.pos.catalog.application.ProductData;
import com.medinasystems.pos.catalog.application.ProductService;
import com.medinasystems.pos.catalog.application.ProductTaxData;
import com.medinasystems.pos.catalog.application.ProductTaxView;
import com.medinasystems.pos.catalog.application.ProductView;
import com.medinasystems.pos.catalog.domain.ProductType;
import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.shared.web.PageResponse;

/** Products and their taxes per country. DELETE deactivates the product. */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    public PageResponse<ProductView> search(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) ProductType type,
                                            @RequestParam(required = false) Boolean active,
                                            @PageableDefault(sort = "name") Pageable pageable) {
        return PageResponse.from(products.search(q, categoryId, type, active, pageable));
    }

    @GetMapping("/{id}")
    public ProductView get(@PathVariable Long id) {
        return products.get(id);
    }

    @GetMapping("/barcode/{code}")
    public ProductView getByBarcode(@PathVariable String code) {
        return products.getByBarcode(code);
    }

    @GetMapping("/sku/{sku}")
    public ProductView getBySku(@PathVariable String sku) {
        return products.getBySku(sku);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CentralOnly
    public ProductView create(@Valid @RequestBody ProductRequest request) {
        return products.create(request.toData());
    }

    @PutMapping("/{id}")
    @CentralOnly
    public ProductView update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return products.update(id, request.toData());
    }

    @DeleteMapping("/{id}")
    @CentralOnly
    public ProductView deactivate(@PathVariable Long id) {
        return products.deactivate(id);
    }

    @GetMapping("/{id}/taxes")
    public List<ProductTaxView> getTaxes(@PathVariable Long id) {
        return products.getTaxes(id);
    }

    /** The body replaces the whole list. An empty list removes every country tax. */
    @PutMapping("/{id}/taxes")
    @CentralOnly
    public List<ProductTaxView> replaceTaxes(@PathVariable Long id,
                                             @Valid @RequestBody List<@Valid ProductTaxRequest> request) {
        return products.replaceTaxes(id, request.stream()
                .map(row -> new ProductTaxData(row.countryId(), row.taxId()))
                .toList());
    }

    /**
     * Omitted flags take their defaults: inventory tracked, no serials, no exclusive invoice,
     * GOOD type, active. The domain applies the rules between them (for example, a SERVICE never tracks inventory).
     */
    public record ProductRequest(@NotBlank @Size(max = 40) String sku,
                                 @Size(max = 50) String barcode,
                                 @NotBlank @Size(max = 200) String name,
                                 String description,
                                 Long categoryId,
                                 @NotNull Long taxId,
                                 @NotNull Long unitOfMeasureId,
                                 @NotNull BigDecimal salePrice,
                                 BigDecimal cost,
                                 ProductType productType,
                                 Boolean tracksInventory,
                                 Boolean tracksSerials,
                                 Boolean requiresExclusiveInvoice,
                                 BigDecimal minStock,
                                 @Size(max = 300) String imageUrl,
                                 Boolean active) {

        ProductData toData() {
            return new ProductData(sku, barcode, name, description, categoryId, taxId, unitOfMeasureId,
                    salePrice, cost, productType == null ? ProductType.GOOD : productType,
                    tracksInventory == null || tracksInventory,
                    Boolean.TRUE.equals(tracksSerials),
                    Boolean.TRUE.equals(requiresExclusiveInvoice),
                    minStock, imageUrl, active == null || active);
        }
    }

    public record ProductTaxRequest(@NotNull Long countryId, @NotNull Long taxId) {
    }
}
