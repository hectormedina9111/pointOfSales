package com.medinasystems.pos.organization.application;

import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.organization.CountryDirectory;
import com.medinasystems.pos.organization.domain.Country;
import com.medinasystems.pos.organization.infrastructure.CompanyRepository;
import com.medinasystems.pos.organization.infrastructure.CountryRepository;

/** Answers {@link CountryDirectory} from the country table. */
@Component
public class DefaultCountryDirectory implements CountryDirectory {

    private final CountryRepository countries;
    private final CompanyRepository companies;

    public DefaultCountryDirectory(CountryRepository countries, CompanyRepository companies) {
        this.countries = countries;
        this.companies = companies;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(long countryId) {
        return countries.existsByIdAndActiveTrue(countryId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findIdByIsoCode(String isoCode) {
        return countries.findByIsoCode(isoCode).map(Country::getId);
    }

    /**
     * The country's own time zone. Stores do not replicate countries, so there the row is missing and the time zone
     * of the company is used instead (the company is replicated and a store works in the company's country).
     * Empty only when neither exists.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ZoneId> timeZoneOf(long countryId) {
        return countries.findById(countryId)
                .map(country -> ZoneId.of(country.getTimeZone()))
                .or(() -> companies.findFirstByOrderByIdAsc().map(company -> ZoneId.of(company.getTimeZone())));
    }
}
