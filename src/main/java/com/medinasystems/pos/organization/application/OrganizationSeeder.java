package com.medinasystems.pos.organization.application;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.organization.domain.FiscalProviderCode;
import com.medinasystems.pos.organization.infrastructure.CompanyRepository;
import com.medinasystems.pos.organization.infrastructure.CountryRepository;

/**
 * Initial organization data, central node only: the country Honduras and the example company. Both are created
 * only when missing, so every startup is safe. No branches are created here.
 *
 * <p>This is Java code and not a Flyway migration on purpose: rows created by a migration would carry central ids
 * in every store database before replication, and the store id ranges must start clean.
 */
@Component
@Profile("central")
@Order(10)
public class OrganizationSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrganizationSeeder.class);

    static final String HONDURAS_ISO = "HN";
    static final String COMPANY_TAX_ID = "08019000000000";

    private final CountryRepository countries;
    private final CompanyRepository companies;
    private final CountryService countryService;
    private final CompanyService companyService;

    public OrganizationSeeder(CountryRepository countries, CompanyRepository companies, CountryService countryService,
                              CompanyService companyService) {
        this.countries = countries;
        this.companies = companies;
        this.countryService = countryService;
        this.companyService = companyService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> created = new ArrayList<>();
        if (!countries.existsByIsoCode(HONDURAS_ISO)) {
            countryService.create(new CountryData(HONDURAS_ISO, "Honduras", "HNL", "America/Tegucigalpa", "RTN",
                    FiscalProviderCode.HN_SAR, true));
            created.add("country Honduras");
        }
        if (companies.count() == 0) {
            companyService.configure(new CompanyData("Empresa de ejemplo S. de R.L.", null, COMPANY_TAX_ID, null,
                    null, null, "HNL", "America/Tegucigalpa"));
            created.add("company Empresa de ejemplo S. de R.L.");
        }
        log.info("Initial data loaded: {}", created.isEmpty() ? "nothing to create" : String.join(", ", created));
    }
}
