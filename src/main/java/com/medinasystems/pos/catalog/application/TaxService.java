package com.medinasystems.pos.catalog.application;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.domain.Tax;
import com.medinasystems.pos.catalog.infrastructure.ProductRepository;
import com.medinasystems.pos.catalog.infrastructure.ProductTaxRepository;
import com.medinasystems.pos.catalog.infrastructure.TaxRepository;
import com.medinasystems.pos.organization.CountryDirectory;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/** Taxes per country. A tax in use cannot be deactivated. */
@Service
@Transactional
public class TaxService {

    private final TaxRepository taxes;
    private final ProductRepository products;
    private final ProductTaxRepository productTaxes;
    private final CountryDirectory countries;
    private final ApplicationEventPublisher events;

    public TaxService(TaxRepository taxes, ProductRepository products, ProductTaxRepository productTaxes,
                      CountryDirectory countries, ApplicationEventPublisher events) {
        this.taxes = taxes;
        this.products = products;
        this.productTaxes = productTaxes;
        this.countries = countries;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<TaxView> list(Long countryId, Pageable pageable) {
        Page<Tax> page = countryId == null ? taxes.findAll(pageable) : taxes.findByCountryId(countryId, pageable);
        return page.map(TaxView::from);
    }

    @Transactional(readOnly = true)
    public TaxView get(Long id) {
        return TaxView.from(find(id));
    }

    public TaxView create(TaxData data) {
        if (!countries.isActive(data.countryId())) {
            throw new BusinessRuleException("COUNTRY_INACTIVE", "The country does not exist or is inactive");
        }
        if (taxes.existsByCountryIdAndCode(data.countryId(), data.code())) {
            throw duplicatedCode();
        }
        Tax tax = new Tax(data.countryId(), data.code(), data.name(), data.rate(), data.active());
        Tax saved = taxes.save(tax);
        events.publishEvent(AuditEvent.created("TAX_CREATED", "tax", saved.getId(), snapshot(saved)));
        return TaxView.from(saved);
    }

    public TaxView update(Long id, TaxData data) {
        Tax tax = find(id);
        if (taxes.existsByCountryIdAndCodeAndIdNot(data.countryId(), data.code(), id)) {
            throw duplicatedCode();
        }
        Map<String, Object> before = snapshot(tax);
        boolean inUse = !data.active() && isInUse(id);
        tax.update(data.countryId(), data.code(), data.name(), data.rate(), data.active(), inUse);
        events.publishEvent(AuditEvent.updated("TAX_UPDATED", "tax", id, before, snapshot(tax)));
        return TaxView.from(tax);
    }

    private boolean isInUse(Long taxId) {
        return products.existsByTaxIdAndActiveTrue(taxId) || productTaxes.existsByTaxId(taxId);
    }

    private Tax find(Long id) {
        return taxes.findById(id).orElseThrow(() -> new ResourceNotFoundException("tax", id));
    }

    private static BusinessRuleException duplicatedCode() {
        return new BusinessRuleException("DUPLICATE_TAX_CODE", "The tax code is already used in this country");
    }

    private static Map<String, Object> snapshot(Tax tax) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("countryId", tax.getCountryId());
        values.put("code", tax.getCode());
        values.put("name", tax.getName());
        values.put("rate", tax.getRate());
        values.put("active", tax.isActive());
        return values;
    }
}
