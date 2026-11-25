package com.medinasystems.pos.organization.application;

import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.organization.CountryDirectory;
import com.medinasystems.pos.organization.domain.Country;
import com.medinasystems.pos.organization.infrastructure.CountryRepository;

/** Answers {@link CountryDirectory} from the country table. */
@Component
public class DefaultCountryDirectory implements CountryDirectory {

    private final CountryRepository countries;

    public DefaultCountryDirectory(CountryRepository countries) {
        this.countries = countries;
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

    @Override
    @Transactional(readOnly = true)
    public Optional<ZoneId> timeZoneOf(long countryId) {
        return countries.findById(countryId).map(country -> ZoneId.of(country.getTimeZone()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> defaultCountryId() {
        return countries.findFirstByOrderByIdAsc().map(Country::getId);
    }
}
