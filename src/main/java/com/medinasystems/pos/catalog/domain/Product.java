package com.medinasystems.pos.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;
import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * A sellable product or service. Prices always include tax, so the taxable base is calculated
 * backwards. Products are never deleted: they are deactivated.
 */
@Entity
@Table(name = "product")
public class Product extends AuditableEntity {

    private static final int MONEY_SCALE = 2;
    private static final int QUANTITY_SCALE = 3;

    @Column(nullable = false, unique = true, length = 40)
    private String sku;

    @Column(unique = true, length = 50)
    private String barcode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tax_id", nullable = false)
    private Tax tax;

    @ManyToOne(optional = false)
    @JoinColumn(name = "unit_of_measure_id", nullable = false)
    private UnitOfMeasure unitOfMeasure;

    @Column(name = "sale_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "price_includes_tax", nullable = false)
    private boolean priceIncludesTax = true;

    @Column(precision = 14, scale = 2)
    private BigDecimal cost;

    @Column(name = "tracks_inventory", nullable = false)
    private boolean tracksInventory = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "serial_mode", nullable = false, length = 10)
    private SerialMode serialMode = SerialMode.NONE;

    @Column(name = "requires_exclusive_invoice", nullable = false)
    private boolean requiresExclusiveInvoice;

    @Column(name = "min_stock", nullable = false, precision = 12, scale = 3)
    private BigDecimal minStock = BigDecimal.ZERO;

    @Column(name = "image_url", length = 300)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 10)
    private ProductType productType = ProductType.GOOD;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("countryId ASC")
    private List<ProductTax> productTaxes = new ArrayList<>();

    protected Product() {
    }

    public Product(ProductDetails details) {
        apply(details, true);
    }

    /** Changes every field; the tax and unit must be active only when they change or the product is new. */
    public void update(ProductDetails details) {
        apply(details, false);
    }

    /** Deactivates the product; it stays in the history and in the catalog. */
    public void deactivate() {
        this.active = false;
    }

    private void apply(ProductDetails d, boolean creating) {
        Objects.requireNonNull(d.sku(), "sku is required");
        Objects.requireNonNull(d.name(), "name is required");
        Objects.requireNonNull(d.tax(), "tax is required");
        Objects.requireNonNull(d.unitOfMeasure(), "unit of measure is required");
        Objects.requireNonNull(d.productType(), "product type is required");
        Objects.requireNonNull(d.salePrice(), "sale price is required");

        if (creating || !sameId(d.tax(), tax)) {
            requireActiveTax(d.tax());
        }
        if (creating || !sameId(d.unitOfMeasure(), unitOfMeasure)) {
            requireActiveUnit(d.unitOfMeasure());
        }

        SerialMode mode = d.serialMode() == null ? SerialMode.NONE : d.serialMode();
        boolean service = d.productType() == ProductType.SERVICE;
        if (service && mode.hasSerials()) {
            throw new BusinessRuleException("SERVICE_WITH_SERIALS", "A service cannot track serial numbers");
        }
        // Un servicio nunca mueve inventario: el valor se fuerza a false en lugar de rechazarse.
        boolean inventory = !service && d.tracksInventory();
        if (mode.hasSerials() && !inventory) {
            throw new BusinessRuleException("SERIALS_REQUIRE_INVENTORY",
                    "Serial numbers require tracking inventory");
        }
        if (mode.hasSerials() && d.unitOfMeasure().isAllowsDecimals()) {
            throw new BusinessRuleException("SERIALS_REQUIRE_WHOLE_UNIT",
                    "Serial numbers require a unit without decimals");
        }

        BigDecimal price = money(d.salePrice());
        BigDecimal unitCost = d.cost() == null ? null : money(d.cost());
        BigDecimal stock = d.minStock() == null ? BigDecimal.ZERO : d.minStock();
        if (stock.signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_QUANTITY", "The minimum stock cannot be negative");
        }
        stock = stock.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);

        this.sku = d.sku();
        this.barcode = normalizeBarcode(d.barcode());
        this.name = d.name();
        this.description = d.description();
        this.category = d.category();
        this.tax = d.tax();
        this.unitOfMeasure = d.unitOfMeasure();
        this.salePrice = price;
        this.cost = unitCost;
        this.productType = d.productType();
        this.tracksInventory = inventory;
        this.serialMode = mode;
        this.requiresExclusiveInvoice = d.requiresExclusiveInvoice();
        this.minStock = stock;
        this.imageUrl = d.imageUrl();
        this.active = d.active();
    }

    private void requireActiveTax(Tax candidate) {
        if (!candidate.isActive()) {
            throw new BusinessRuleException("TAX_INACTIVE", "The tax of the product is inactive");
        }
    }

    private void requireActiveUnit(UnitOfMeasure candidate) {
        if (!candidate.isActive()) {
            throw new BusinessRuleException("UNIT_INACTIVE", "The unit of measure is inactive");
        }
    }

    /**
     * Sets the tax of each listed country. Rows of other countries are removed. Existing rows are
     * updated in place, so the unique (product, country) key is never violated during the flush.
     * Every tax is validated before anything changes.
     */
    public void replaceCountryTaxes(Map<Long, Tax> taxesByCountry) {
        taxesByCountry.forEach(ProductTax::requireValidTax);
        productTaxes.removeIf(row -> !taxesByCountry.containsKey(row.getCountryId()));
        taxesByCountry.forEach((countryId, tax) -> productTaxes.stream()
                .filter(row -> row.getCountryId().equals(countryId))
                .findFirst()
                .ifPresentOrElse(row -> row.changeTax(tax),
                        () -> productTaxes.add(new ProductTax(this, countryId, tax))));
    }

    /** Barcodes are optional: a blank value means no barcode. */
    public static String normalizeBarcode(String barcode) {
        return barcode == null || barcode.isBlank() ? null : barcode.trim();
    }

    private static BigDecimal money(BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_AMOUNT", "The amount cannot be negative");
        }
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static boolean sameId(AuditableEntity candidate, AuditableEntity current) {
        return current != null && candidate.getId() != null && candidate.getId().equals(current.getId());
    }

    public String getSku() {
        return sku;
    }

    public String getBarcode() {
        return barcode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public Tax getTax() {
        return tax;
    }

    public UnitOfMeasure getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public boolean isPriceIncludesTax() {
        return priceIncludesTax;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public boolean isTracksInventory() {
        return tracksInventory;
    }

    public SerialMode getSerialMode() {
        return serialMode;
    }

    public boolean isRequiresExclusiveInvoice() {
        return requiresExclusiveInvoice;
    }

    public BigDecimal getMinStock() {
        return minStock;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isActive() {
        return active;
    }

    public ProductType getProductType() {
        return productType;
    }

    public List<ProductTax> getProductTaxes() {
        return Collections.unmodifiableList(productTaxes);
    }
}
