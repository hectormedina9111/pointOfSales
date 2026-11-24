package com.medinasystems.pos.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;
import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * A tax of one country, such as ISV 15 % in Honduras. The country is kept as an id: the
 * catalog never holds organization entities.
 */
@Entity
@Table(name = "tax")
public class Tax extends AuditableEntity {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Column(name = "country_id", nullable = false)
    private Long countryId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal rate;

    @Column(nullable = false)
    private boolean active = true;

    protected Tax() {
    }

    public Tax(Long countryId, String code, String name, BigDecimal rate, boolean active) {
        this.countryId = Objects.requireNonNull(countryId, "countryId is required");
        this.code = Objects.requireNonNull(code, "code is required");
        this.name = Objects.requireNonNull(name, "name is required");
        this.rate = validRate(rate);
        this.active = active;
    }

    /**
     * The country cannot change: products already point to this tax as belonging to it.
     * {@code inUse} tells whether active products or product taxes use it.
     */
    public void update(Long countryId, String code, String name, BigDecimal rate, boolean active, boolean inUse) {
        if (!this.countryId.equals(countryId)) {
            throw new BusinessRuleException("TAX_COUNTRY_IMMUTABLE", "The country of a tax cannot be changed");
        }
        if (!active && inUse) {
            throw new BusinessRuleException("TAX_IN_USE", "The tax is used by products and cannot be deactivated");
        }
        BigDecimal checkedRate = validRate(rate);
        this.code = Objects.requireNonNull(code, "code is required");
        this.name = Objects.requireNonNull(name, "name is required");
        this.rate = checkedRate;
        this.active = active;
    }

    private static BigDecimal validRate(BigDecimal rate) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(HUNDRED) > 0) {
            throw new BusinessRuleException("INVALID_TAX_RATE", "The tax rate must be between 0 and 100");
        }
        return rate.setScale(2, RoundingMode.HALF_UP);
    }

    public Long getCountryId() {
        return countryId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public boolean isActive() {
        return active;
    }
}
