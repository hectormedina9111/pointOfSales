package com.medinasystems.pos.organization.application;

import com.medinasystems.pos.organization.domain.Country;
import com.medinasystems.pos.organization.domain.FiscalProviderCode;

public record CountryView(Long id, String isoCode, String name, String currency, String timeZone,
                          String taxIdLabel, FiscalProviderCode fiscalProvider, boolean active) {

    public static CountryView from(Country country) {
        return new CountryView(country.getId(), country.getIsoCode(), country.getName(), country.getCurrency(),
                country.getTimeZone(), country.getTaxIdLabel(), country.getFiscalProvider(), country.isActive());
    }
}
