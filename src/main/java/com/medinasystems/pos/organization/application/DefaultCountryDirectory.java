package com.medinasystems.pos.organization.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.organization.CountryDirectory;
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
}
