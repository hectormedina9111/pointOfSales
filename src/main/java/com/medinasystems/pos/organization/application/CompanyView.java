package com.medinasystems.pos.organization.application;

import com.medinasystems.pos.organization.domain.Company;

public record CompanyView(Long id, String legalName, String tradeName, String taxId, String address, String phone,
                          String email, String currency, String timeZone) {

    public static CompanyView from(Company company) {
        return new CompanyView(company.getId(), company.getLegalName(), company.getTradeName(), company.getTaxId(),
                company.getAddress(), company.getPhone(), company.getEmail(), company.getCurrency(),
                company.getTimeZone());
    }
}
