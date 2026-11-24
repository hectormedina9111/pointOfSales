package com.medinasystems.pos.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * The company that owns the branches. The application keeps a single row: the central node
 * creates it the first time it is configured.
 */
@Entity
@Table(name = "company")
public class Company extends AuditableEntity {

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(name = "tax_id", nullable = false, unique = true, length = 14)
    private String taxId;

    @Column(length = 300)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    protected Company() {
    }

    public Company(String legalName, String tradeName, String taxId, String address, String phone, String email,
                   String currency, String timeZone) {
        update(legalName, tradeName, taxId, address, phone, email, currency, timeZone);
    }

    public void update(String legalName, String tradeName, String taxId, String address, String phone,
                       String email, String currency, String timeZone) {
        String validCurrency = Rules.requireCurrency(currency);
        String validTimeZone = Rules.requireTimeZone(timeZone);
        this.currency = validCurrency;
        this.timeZone = validTimeZone;
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.taxId = taxId;
        this.address = address;
        this.phone = phone;
        this.email = email;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public String getTaxId() {
        return taxId;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getCurrency() {
        return currency;
    }

    public String getTimeZone() {
        return timeZone;
    }
}
