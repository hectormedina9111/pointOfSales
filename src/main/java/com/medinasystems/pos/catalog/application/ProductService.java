package com.medinasystems.pos.catalog.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.domain.Category;
import com.medinasystems.pos.catalog.domain.Product;
import com.medinasystems.pos.catalog.domain.ProductDetails;
import com.medinasystems.pos.catalog.domain.ProductType;
import com.medinasystems.pos.catalog.domain.Tax;
import com.medinasystems.pos.catalog.domain.UnitOfMeasure;
import com.medinasystems.pos.catalog.infrastructure.CategoryRepository;
import com.medinasystems.pos.catalog.infrastructure.ProductRepository;
import com.medinasystems.pos.catalog.infrastructure.ProductSpecifications;
import com.medinasystems.pos.catalog.infrastructure.TaxRepository;
import com.medinasystems.pos.catalog.infrastructure.UnitOfMeasureRepository;
import com.medinasystems.pos.organization.CountryDirectory;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Products and the tax of each country. Price and cost changes are recorded twice: as a normal
 * update and as a PRICE_CHANGED event with only the prices, so price history is easy to query.
 */
@Service
@Transactional
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final TaxRepository taxes;
    private final UnitOfMeasureRepository units;
    private final CountryDirectory countries;
    private final ApplicationEventPublisher events;

    public ProductService(ProductRepository products, CategoryRepository categories, TaxRepository taxes,
                          UnitOfMeasureRepository units, CountryDirectory countries,
                          ApplicationEventPublisher events) {
        this.products = products;
        this.categories = categories;
        this.taxes = taxes;
        this.units = units;
        this.countries = countries;
        this.events = events;
    }

    /** Products are global: the search does not filter by branch. */
    @Transactional(readOnly = true)
    public Page<ProductView> search(String q, Long categoryId, ProductType type, Boolean active, Pageable pageable) {
        return products.findAll(ProductSpecifications.matching(q, categoryId, type, active), pageable)
                .map(ProductView::from);
    }

    @Transactional(readOnly = true)
    public ProductView get(Long id) {
        return ProductView.from(find(id));
    }

    /** Returns the product whether it is active or not, so past sales can still be looked up. */
    @Transactional(readOnly = true)
    public ProductView getByBarcode(String barcode) {
        return products.findByBarcode(barcode)
                .map(ProductView::from)
                .orElseThrow(() -> new ResourceNotFoundException("product", barcode));
    }

    @Transactional(readOnly = true)
    public ProductView getBySku(String sku) {
        return products.findBySku(sku)
                .map(ProductView::from)
                .orElseThrow(() -> new ResourceNotFoundException("product", sku));
    }

    public ProductView create(ProductData data) {
        String barcode = Product.normalizeBarcode(data.barcode());
        requireUniqueSku(data.sku(), null);
        requireUniqueBarcode(barcode, null);
        Product product = new Product(resolve(data, barcode));
        Product saved = products.save(product);
        events.publishEvent(AuditEvent.created("PRODUCT_CREATED", "product", saved.getId(), snapshot(saved)));
        return ProductView.from(saved);
    }

    public ProductView update(Long id, ProductData data) {
        Product product = find(id);
        String barcode = Product.normalizeBarcode(data.barcode());
        requireUniqueSku(data.sku(), id);
        requireUniqueBarcode(barcode, id);
        Map<String, Object> before = snapshot(product);
        Map<String, Object> beforePrices = prices(product);
        product.update(resolve(data, barcode));
        events.publishEvent(AuditEvent.updated("PRODUCT_UPDATED", "product", id, before, snapshot(product)));
        AuditEvent priceChange = AuditEvent.updated("PRICE_CHANGED", "product", id, beforePrices, prices(product));
        if (!priceChange.newValues().isEmpty()) {
            events.publishEvent(priceChange);
        }
        return ProductView.from(product);
    }

    /** DELETE deactivates the product. */
    public ProductView deactivate(Long id) {
        Product product = find(id);
        Map<String, Object> before = snapshot(product);
        product.deactivate();
        events.publishEvent(AuditEvent.updated("PRODUCT_DEACTIVATED", "product", id, before, snapshot(product)));
        return ProductView.from(product);
    }

    @Transactional(readOnly = true)
    public List<ProductTaxView> getTaxes(Long productId) {
        return find(productId).getProductTaxes().stream().map(ProductTaxView::from).toList();
    }

    /**
     * Replaces the country taxes of the product with the given list. An empty list removes them all,
     * and the product then uses its main tax everywhere.
     */
    public List<ProductTaxView> replaceTaxes(Long productId, List<ProductTaxData> rows) {
        Product product = find(productId);
        Map<String, Object> before = taxSnapshot(product);
        Map<Long, Tax> byCountry = new LinkedHashMap<>();
        for (ProductTaxData row : rows) {
            if (byCountry.containsKey(row.countryId())) {
                throw new BusinessRuleException("DUPLICATE_PRODUCT_TAX_COUNTRY",
                        "Each country can appear only once in the product taxes");
            }
            if (!countries.isActive(row.countryId())) {
                throw new BusinessRuleException("COUNTRY_INACTIVE", "The country does not exist or is inactive");
            }
            byCountry.put(row.countryId(), findTax(row.taxId()));
        }
        product.replaceCountryTaxes(byCountry);
        events.publishEvent(AuditEvent.updated("PRODUCT_TAXES_UPDATED", "product", productId, before,
                taxSnapshot(product)));
        return product.getProductTaxes().stream().map(ProductTaxView::from).toList();
    }

    private ProductDetails resolve(ProductData data, String barcode) {
        Category category = data.categoryId() == null ? null : findCategory(data.categoryId());
        return new ProductDetails(data.sku(), barcode, data.name(), data.description(), category,
                findTax(data.taxId()), findUnit(data.unitOfMeasureId()), data.salePrice(), data.cost(),
                data.productType(), data.tracksInventory(), data.tracksSerials(),
                data.requiresExclusiveInvoice(), data.minStock(), data.imageUrl(), data.active());
    }

    private void requireUniqueSku(String sku, Long id) {
        boolean taken = id == null ? products.existsBySku(sku) : products.existsBySkuAndIdNot(sku, id);
        if (taken) {
            throw new BusinessRuleException("DUPLICATE_SKU", "The SKU is already in use");
        }
    }

    private void requireUniqueBarcode(String barcode, Long id) {
        if (barcode == null) {
            return;
        }
        boolean taken = id == null ? products.existsByBarcode(barcode) : products.existsByBarcodeAndIdNot(barcode, id);
        if (taken) {
            throw new BusinessRuleException("DUPLICATE_BARCODE", "The barcode is already in use");
        }
    }

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("product", id));
    }

    private Category findCategory(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new BusinessRuleException("CATEGORY_NOT_FOUND", "The category does not exist"));
    }

    private Tax findTax(Long id) {
        return taxes.findById(id)
                .orElseThrow(() -> new BusinessRuleException("TAX_NOT_FOUND", "The tax does not exist"));
    }

    private UnitOfMeasure findUnit(Long id) {
        return units.findById(id)
                .orElseThrow(() -> new BusinessRuleException("UNIT_NOT_FOUND", "The unit of measure does not exist"));
    }

    private static Map<String, Object> snapshot(Product product) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sku", product.getSku());
        values.put("barcode", product.getBarcode());
        values.put("name", product.getName());
        values.put("categoryId", product.getCategory() == null ? null : product.getCategory().getId());
        values.put("taxId", product.getTax().getId());
        values.put("unitOfMeasureId", product.getUnitOfMeasure().getId());
        values.put("salePrice", product.getSalePrice());
        values.put("cost", product.getCost());
        values.put("productType", product.getProductType().name());
        values.put("tracksInventory", product.isTracksInventory());
        values.put("tracksSerials", product.isTracksSerials());
        values.put("requiresExclusiveInvoice", product.isRequiresExclusiveInvoice());
        values.put("minStock", product.getMinStock());
        values.put("active", product.isActive());
        return values;
    }

    private static Map<String, Object> prices(Product product) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("salePrice", product.getSalePrice());
        values.put("cost", product.getCost());
        return values;
    }

    private static Map<String, Object> taxSnapshot(Product product) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("taxes", product.getProductTaxes().stream()
                .map(row -> Map.of("countryId", row.getCountryId(), "taxId", row.getTax().getId()))
                .toList());
        return values;
    }
}
