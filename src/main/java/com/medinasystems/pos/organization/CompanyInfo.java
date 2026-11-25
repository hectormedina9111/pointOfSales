package com.medinasystems.pos.organization;

/**
 * What the sales tickets print about the company. {@code timeZone} is the IANA zone of the company, such as
 * {@code America/Tegucigalpa}; ticket times are shown in it.
 */
public record CompanyInfo(String legalName, String tradeName, String taxId, String timeZone) {
}
