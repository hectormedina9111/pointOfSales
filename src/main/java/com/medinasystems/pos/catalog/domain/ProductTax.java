package com.medinasystems.pos.catalog.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;
import com.medinasystems.pos.shared.BusinessRuleException;

/** The tax of a product in one country, used when that country differs from the product's main tax. */
@Entity
@Table(name = "product_tax")
public class ProductTax extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "country_id", nullable = false)
    private Long countryId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tax_id", nullable = false)
    private Tax tax;

    protected ProductTax() {
    }

    ProductTax(Product product, Long countryId, Tax tax) {
        this.product = Objects.requireNonNull(product, "product is required");
        this.countryId = Objects.requireNonNull(countryId, "countryId is required");
        this.tax = requireValidTax(countryId, tax);
    }

    void changeTax(Tax newTax) {
        this.tax = requireValidTax(countryId, newTax);
    }

    /** The tax must be active and must belong to the country of the row. */
    static Tax requireValidTax(Long countryId, Tax tax) {
        Objects.requireNonNull(tax, "tax is required");
        if (!tax.getCountryId().equals(countryId)) {
            throw new BusinessRuleException("TAX_COUNTRY_MISMATCH", "The tax does not belong to the given country");
        }
        if (!tax.isActive()) {
            throw new BusinessRuleException("TAX_INACTIVE", "The tax is inactive");
        }
        return tax;
    }

    public Long getCountryId() {
        return countryId;
    }

    public Tax getTax() {
        return tax;
    }
}
