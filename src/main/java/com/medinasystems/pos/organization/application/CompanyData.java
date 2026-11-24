package com.medinasystems.pos.organization.application;

/** Input for configuring the company. */
public record CompanyData(String legalName, String tradeName, String taxId, String address, String phone,
                          String email, String currency, String timeZone) {
}
