package com.medinasystems.pos.organization.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/** A country where branches operate. It defines the currency, time zone and fiscal provider. */
@Entity
@Table(name = "country")
public class Country extends AuditableEntity {

    @Column(name = "iso_code", nullable = false, unique = true, length = 2)
    private String isoCode;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Column(name = "tax_id_label", nullable = false, length = 20)
    private String taxIdLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "fiscal_provider", nullable = false, length = 30)
    private FiscalProviderCode fiscalProvider;

    @Column(nullable = false)
    private boolean active = true;

    protected Country() {
    }

    public Country(String isoCode, String name, String currency, String timeZone, String taxIdLabel,
                   FiscalProviderCode fiscalProvider) {
        update(isoCode, name, currency, timeZone, taxIdLabel, fiscalProvider, true);
    }

    /** Validates every rule before changing anything, so a rejected update leaves the country as it was. */
    public void update(String isoCode, String name, String currency, String timeZone, String taxIdLabel,
                       FiscalProviderCode fiscalProvider, boolean active) {
        String validIsoCode = Rules.requireIsoCode(isoCode);
        String validCurrency = Rules.requireCurrency(currency);
        String validTimeZone = Rules.requireTimeZone(timeZone);
        this.isoCode = validIsoCode;
        this.currency = validCurrency;
        this.timeZone = validTimeZone;
        this.name = name;
        this.taxIdLabel = taxIdLabel;
        this.fiscalProvider = Objects.requireNonNull(fiscalProvider, "fiscalProvider is required");
        this.active = active;
    }

    public String getIsoCode() {
        return isoCode;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getTaxIdLabel() {
        return taxIdLabel;
    }

    public FiscalProviderCode getFiscalProvider() {
        return fiscalProvider;
    }

    public boolean isActive() {
        return active;
    }
}
