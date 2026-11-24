package com.medinasystems.pos.catalog.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.catalog.infrastructure.TaxRepository;
import com.medinasystems.pos.catalog.infrastructure.UnitOfMeasureRepository;
import com.medinasystems.pos.organization.CountryDirectory;

/**
 * Initial catalog data, central node only: units of measure and the Honduran taxes. Each row is created only when
 * its code does not exist yet, so every startup is safe. It runs after the organization seeder, which creates
 * Honduras. Created through the services, so the same rules and audit events apply.
 */
@Component
@Profile("central")
@Order(20)
public class CatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);
    private static final String HONDURAS_ISO = "HN";

    private final UnitOfMeasureRepository units;
    private final TaxRepository taxes;
    private final UnitOfMeasureService unitService;
    private final TaxService taxService;
    private final CountryDirectory countries;

    public CatalogSeeder(UnitOfMeasureRepository units, TaxRepository taxes, UnitOfMeasureService unitService,
                         TaxService taxService, CountryDirectory countries) {
        this.units = units;
        this.taxes = taxes;
        this.unitService = unitService;
        this.taxService = taxService;
        this.countries = countries;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> created = new ArrayList<>();
        createUnit("UNIT", "Unidad", false, created);
        createUnit("KG", "Kilogramo", true, created);
        createUnit("LB", "Libra", true, created);
        createUnit("LT", "Litro", true, created);

        Long countryId = honduras();
        createTax(countryId, "EXEMPT", "Exento", "0.00", created);
        createTax(countryId, "ISV15", "ISV 15 %", "15.00", created);
        createTax(countryId, "ISV18", "ISV 18 %", "18.00", created);

        log.info("Initial data loaded: {}", created.isEmpty() ? "nothing to create" : String.join(", ", created));
    }

    private void createUnit(String code, String name, boolean allowsDecimals, List<String> created) {
        if (units.existsByCode(code)) {
            return;
        }
        unitService.create(new UnitOfMeasureData(code, name, allowsDecimals, true));
        created.add("unit " + code);
    }

    private void createTax(Long countryId, String code, String name, String rate, List<String> created) {
        if (taxes.existsByCountryIdAndCode(countryId, code)) {
            return;
        }
        taxService.create(new TaxData(countryId, code, name, new BigDecimal(rate), true));
        created.add("tax " + code);
    }

    private Long honduras() {
        Optional<Long> id = countries.findIdByIsoCode(HONDURAS_ISO);
        return id.orElseThrow(() -> new IllegalStateException(
                "Country " + HONDURAS_ISO + " is missing; OrganizationSeeder must run first"));
    }
}
