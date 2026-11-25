package com.medinasystems.pos.organization.application;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.organization.domain.Country;
import com.medinasystems.pos.organization.infrastructure.CountryRepository;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/** Countries: create and change. Validation of each field lives in the {@link Country} entity. */
@Service
@Transactional
public class CountryService {

    private final CountryRepository countries;
    private final ApplicationEventPublisher events;

    public CountryService(CountryRepository countries, ApplicationEventPublisher events) {
        this.countries = countries;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<CountryView> list(Pageable pageable) {
        return countries.findAll(pageable).map(CountryView::from);
    }

    @Transactional(readOnly = true)
    public CountryView get(Long id) {
        return CountryView.from(find(id));
    }

    public CountryView create(CountryData data) {
        if (countries.count() > 0) {
            throw new BusinessRuleException("SINGLE_COUNTRY_ONLY",
                    "Only one country can be configured: each central server serves one country.");
        }
        Country country = new Country(data.isoCode(), data.name(), data.currency(), data.timeZone(),
                data.taxIdLabel(), data.fiscalProvider());
        if (countries.existsByIsoCode(country.getIsoCode())) {
            throw new BusinessRuleException("DUPLICATE_ISO_CODE", "The country ISO code is already in use");
        }
        country.update(data.isoCode(), data.name(), data.currency(), data.timeZone(), data.taxIdLabel(),
                data.fiscalProvider(), data.active());
        Country saved = countries.save(country);
        events.publishEvent(AuditEvent.created("COUNTRY_CREATED", "country", saved.getId(), snapshot(saved)));
        return CountryView.from(saved);
    }

    /** The duplicate check runs before the change: a flush of a duplicated code would fail as a database error. */
    public CountryView update(Long id, CountryData data) {
        Country country = find(id);
        if (countries.existsByIsoCodeAndIdNot(data.isoCode(), id)) {
            throw new BusinessRuleException("DUPLICATE_ISO_CODE", "The country ISO code is already in use");
        }
        Map<String, Object> before = snapshot(country);
        country.update(data.isoCode(), data.name(), data.currency(), data.timeZone(), data.taxIdLabel(),
                data.fiscalProvider(), data.active());
        events.publishEvent(AuditEvent.updated("COUNTRY_UPDATED", "country", id, before, snapshot(country)));
        return CountryView.from(country);
    }

    private Country find(Long id) {
        return countries.findById(id).orElseThrow(() -> new ResourceNotFoundException("country", id));
    }

    private static Map<String, Object> snapshot(Country country) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("isoCode", country.getIsoCode());
        values.put("name", country.getName());
        values.put("currency", country.getCurrency());
        values.put("timeZone", country.getTimeZone());
        values.put("taxIdLabel", country.getTaxIdLabel());
        values.put("fiscalProvider", country.getFiscalProvider().name());
        values.put("active", country.isActive());
        return values;
    }
}
