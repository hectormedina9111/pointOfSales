package com.medinasystems.pos.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CountryTest {

    private static Country honduras() {
        return new Country("HN", "Honduras", "HNL", "America/Tegucigalpa", "RTN", FiscalProviderCode.HN_SAR);
    }

    @Test
    void newCountryIsActive() {
        assertThat(honduras().isActive()).isTrue();
    }

    @Test
    void rejectsIsoCodeThatIsNotTwoUppercaseLetters() {
        assertThatThrownBy(() -> new Country("hn", "Honduras", "HNL", "America/Tegucigalpa", "RTN",
                FiscalProviderCode.HN_SAR))
                .hasFieldOrPropertyWithValue("code", "INVALID_ISO_CODE");
        assertThatThrownBy(() -> new Country("HND", "Honduras", "HNL", "America/Tegucigalpa", "RTN",
                FiscalProviderCode.HN_SAR))
                .hasFieldOrPropertyWithValue("code", "INVALID_ISO_CODE");
    }

    @Test
    void rejectsCurrencyThatIsNotThreeUppercaseLetters() {
        assertThatThrownBy(() -> new Country("HN", "Honduras", "Lempira", "America/Tegucigalpa", "RTN",
                FiscalProviderCode.HN_SAR))
                .hasFieldOrPropertyWithValue("code", "INVALID_CURRENCY");
    }

    @Test
    void rejectsUnknownTimeZone() {
        assertThatThrownBy(() -> new Country("HN", "Honduras", "HNL", "Mars/Olympus", "RTN",
                FiscalProviderCode.HN_SAR))
                .hasFieldOrPropertyWithValue("code", "INVALID_TIME_ZONE");
    }

    @Test
    void rejectedUpdateKeepsThePreviousValues() {
        Country country = honduras();

        assertThatThrownBy(() -> country.update("HN", "Honduras", "HNL", "Mars/Olympus", "RTN",
                FiscalProviderCode.NONE, false))
                .hasFieldOrPropertyWithValue("code", "INVALID_TIME_ZONE");

        assertThat(country.getTimeZone()).isEqualTo("America/Tegucigalpa");
        assertThat(country.getFiscalProvider()).isEqualTo(FiscalProviderCode.HN_SAR);
        assertThat(country.isActive()).isTrue();
    }

    @Test
    void updateCanDeactivateTheCountry() {
        Country country = honduras();

        country.update("HN", "Honduras", "HNL", "America/Tegucigalpa", "RTN", FiscalProviderCode.NONE, false);

        assertThat(country.isActive()).isFalse();
        assertThat(country.getFiscalProvider()).isEqualTo(FiscalProviderCode.NONE);
    }
}
