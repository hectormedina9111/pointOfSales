package com.medinasystems.pos.organization.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CompanyTest {

    @Test
    void rejectsLowercaseCurrency() {
        assertThatThrownBy(() -> new Company("Empresa S.A.", null, "08019999123456", null, null, null,
                "hnl", "America/Tegucigalpa"))
                .hasFieldOrPropertyWithValue("code", "INVALID_CURRENCY");
    }

    @Test
    void rejectsUnknownTimeZone() {
        assertThatThrownBy(() -> new Company("Empresa S.A.", null, "08019999123456", null, null, null,
                "HNL", "Nowhere/Land"))
                .hasFieldOrPropertyWithValue("code", "INVALID_TIME_ZONE");
    }
}
