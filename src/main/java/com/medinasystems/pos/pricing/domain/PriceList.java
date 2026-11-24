package com.medinasystems.pos.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;
import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * A price list, such as RETAIL or WHOLESALE. Its prices always include tax. The country is kept as
 * an id: the pricing module never holds organization entities.
 */
@Entity
@Table(name = "price_list")
public class PriceList extends AuditableEntity {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "country_id", nullable = false)
    private Long countryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_method", nullable = false, length = 15)
    private PricingMethod pricingMethod;

    @Column(precision = 5, scale = 2)
    private BigDecimal percent;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(nullable = false)
    private short priority;

    @Column(nullable = false)
    private boolean active = true;

    protected PriceList() {
    }

    public PriceList(String code, String name, Long countryId, PricingMethod pricingMethod, BigDecimal percent,
                     LocalDate validFrom, LocalDate validTo, short priority, boolean active) {
        apply(code, name, countryId, pricingMethod, percent, validFrom, validTo, priority, active);
    }

    /**
     * Changes every field. {@code hasItems} tells whether the list already has prices per product:
     * those only exist in FIXED_PRICE lists, so the method cannot change to PERCENT_OFF_BASE while they do.
     */
    public void update(String code, String name, Long countryId, PricingMethod pricingMethod, BigDecimal percent,
                       LocalDate validFrom, LocalDate validTo, short priority, boolean active, boolean hasItems) {
        if (hasItems && pricingMethod != PricingMethod.FIXED_PRICE) {
            throw new BusinessRuleException("ITEMS_REQUIRE_FIXED_PRICE",
                    "Only FIXED_PRICE lists can have prices per product");
        }
        apply(code, name, countryId, pricingMethod, percent, validFrom, validTo, priority, active);
    }

    private void apply(String code, String name, Long countryId, PricingMethod pricingMethod, BigDecimal percent,
                       LocalDate validFrom, LocalDate validTo, short priority, boolean active) {
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(countryId, "countryId is required");
        Objects.requireNonNull(pricingMethod, "pricing method is required");
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new BusinessRuleException("INVALID_VALIDITY", "The end of validity is before its start");
        }
        // Un porcentaje solo tiene sentido con PERCENT_OFF_BASE: con FIXED_PRICE se descarta en lugar de rechazarse,
        // igual que el tracksInventory de un servicio.
        this.percent = pricingMethod == PricingMethod.PERCENT_OFF_BASE ? validPercent(percent) : null;
        this.code = code;
        this.name = name;
        this.countryId = countryId;
        this.pricingMethod = pricingMethod;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.priority = priority;
        this.active = active;
    }

    private static BigDecimal validPercent(BigDecimal percent) {
        if (percent == null || percent.signum() < 0 || percent.compareTo(HUNDRED) > 0) {
            throw new BusinessRuleException("INVALID_PERCENT", "The percent must be between 0 and 100");
        }
        return percent.setScale(2, RoundingMode.HALF_UP);
    }

    /** Active, and the date is inside the validity; an empty bound means no limit on that side. */
    public boolean isUsableOn(LocalDate date) {
        return active
                && (validFrom == null || !date.isBefore(validFrom))
                && (validTo == null || !date.isAfter(validTo));
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Long getCountryId() {
        return countryId;
    }

    public PricingMethod getPricingMethod() {
        return pricingMethod;
    }

    public BigDecimal getPercent() {
        return percent;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public short getPriority() {
        return priority;
    }

    public boolean isActive() {
        return active;
    }
}
