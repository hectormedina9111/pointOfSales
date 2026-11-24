package com.medinasystems.pos.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BranchTest {

    private static Company company() {
        return new Company("Empresa S.A.", "Tienda", "08019999123456", null, null, null, "HNL",
                "America/Tegucigalpa");
    }

    private static Country country(boolean active) {
        Country country = new Country("HN", "Honduras", "HNL", "America/Tegucigalpa", "RTN",
                FiscalProviderCode.HN_SAR);
        country.update("HN", "Honduras", "HNL", "America/Tegucigalpa", "RTN", FiscalProviderCode.HN_SAR, active);
        return country;
    }

    private static Branch branch(Country country) {
        return new Branch(company(), country, "001", "Centro", null, null, false, true, null);
    }

    @Test
    void cannotBeCreatedForAnInactiveCountry() {
        assertThatThrownBy(() -> branch(country(false)))
                .hasFieldOrPropertyWithValue("code", "COUNTRY_INACTIVE");
    }

    @Test
    void cannotMoveToAnInactiveCountry() {
        Branch branch = branch(country(true));

        assertThatThrownBy(() -> branch.update(country(false), "001", "Centro", null, null, false, true, null))
                .hasFieldOrPropertyWithValue("code", "COUNTRY_INACTIVE");
        assertThat(branch.getCountry().isActive()).isTrue();
    }

    @Test
    void keepsItsCountryEvenWhenThatCountryIsDeactivatedLater() {
        Country country = country(true);
        Branch branch = branch(country);
        country.update("HN", "Honduras", "HNL", "America/Tegucigalpa", "RTN", FiscalProviderCode.HN_SAR, false);

        branch.update(country, "001", "Centro renombrado", null, null, true, true, null);

        assertThat(branch.getName()).isEqualTo("Centro renombrado");
        assertThat(branch.isAllowNegativeStock()).isTrue();
    }
}
