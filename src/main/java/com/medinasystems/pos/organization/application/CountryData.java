package com.medinasystems.pos.organization.application;

import com.medinasystems.pos.organization.domain.FiscalProviderCode;

/** Input for creating or changing a country. */
public record CountryData(String isoCode, String name, String currency, String timeZone, String taxIdLabel,
                          FiscalProviderCode fiscalProvider, boolean active) {
}
