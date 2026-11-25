package com.medinasystems.pos.organization.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;
import com.medinasystems.pos.shared.BusinessRuleException;

/** A branch (sucursal). In the MVP each store is one branch, still without cash registers. */
@Entity
@Table(name = "branch")
public class Branch extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(optional = false)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(name = "allow_negative_stock", nullable = false)
    private boolean allowNegativeStock;

    @Column(nullable = false)
    private boolean active = true;

    /** True when the branch accepts returns of sales made in other branches. Only the central node changes it. */
    @Column(name = "accepts_foreign_returns", nullable = false)
    private boolean acceptsForeignReturns;

    /** Plain column: the price list table arrives in step 5, which adds the foreign key. */
    @Column(name = "default_price_list_id")
    private Long defaultPriceListId;

    protected Branch() {
    }

    public Branch(Company company, Country country, String code, String name, String address, String phone,
                  boolean allowNegativeStock, boolean active, Long defaultPriceListId,
                  boolean acceptsForeignReturns) {
        this.company = Objects.requireNonNull(company, "company is required");
        this.country = requireActiveCountry(country);
        this.code = code;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.allowNegativeStock = allowNegativeStock;
        this.active = active;
        this.defaultPriceListId = defaultPriceListId;
        this.acceptsForeignReturns = acceptsForeignReturns;
    }

    /**
     * Changing the country requires an active one. Keeping the current country never fails,
     * even if that country was deactivated later.
     */
    public void update(Country newCountry, String code, String name, String address, String phone,
                       boolean allowNegativeStock, boolean active, Long defaultPriceListId,
                       boolean acceptsForeignReturns) {
        if (newCountry != this.country) {
            this.country = requireActiveCountry(newCountry);
        }
        this.code = code;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.allowNegativeStock = allowNegativeStock;
        this.active = active;
        this.defaultPriceListId = defaultPriceListId;
        this.acceptsForeignReturns = acceptsForeignReturns;
    }

    private static Country requireActiveCountry(Country country) {
        Objects.requireNonNull(country, "country is required");
        if (!country.isActive()) {
            throw new BusinessRuleException("COUNTRY_INACTIVE", "A branch cannot belong to an inactive country");
        }
        return country;
    }

    public Company getCompany() {
        return company;
    }

    public Country getCountry() {
        return country;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isAllowNegativeStock() {
        return allowNegativeStock;
    }

    public boolean isActive() {
        return active;
    }

    public Long getDefaultPriceListId() {
        return defaultPriceListId;
    }

    public boolean isAcceptsForeignReturns() {
        return acceptsForeignReturns;
    }
}
