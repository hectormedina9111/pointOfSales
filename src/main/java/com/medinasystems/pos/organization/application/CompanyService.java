package com.medinasystems.pos.organization.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.organization.domain.Company;
import com.medinasystems.pos.organization.infrastructure.CompanyRepository;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/** The company of this installation. It is created by the first call to {@link #configure}. */
@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companies;
    private final ApplicationEventPublisher events;

    public CompanyService(CompanyRepository companies, ApplicationEventPublisher events) {
        this.companies = companies;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public CompanyView get() {
        return companies.findFirstByOrderByIdAsc()
                .map(CompanyView::from)
                .orElseThrow(() -> new ResourceNotFoundException("company", "current"));
    }

    /** Creates the company when it does not exist yet, otherwise changes it. */
    public CompanyView configure(CompanyData data) {
        Optional<Company> existing = companies.findFirstByOrderByIdAsc();
        if (existing.isEmpty()) {
            Company company = new Company(data.legalName(), data.tradeName(), data.taxId(), data.address(),
                    data.phone(), data.email(), data.currency(), data.timeZone());
            if (companies.existsByTaxId(company.getTaxId())) {
                throw duplicatedTaxId();
            }
            Company saved = companies.save(company);
            events.publishEvent(AuditEvent.created("COMPANY_UPDATED", "company", saved.getId(), snapshot(saved)));
            return CompanyView.from(saved);
        }

        Company company = existing.get();
        if (companies.existsByTaxIdAndIdNot(data.taxId(), company.getId())) {
            throw duplicatedTaxId();
        }
        Map<String, Object> before = snapshot(company);
        company.update(data.legalName(), data.tradeName(), data.taxId(), data.address(), data.phone(),
                data.email(), data.currency(), data.timeZone());
        events.publishEvent(AuditEvent.updated("COMPANY_UPDATED", "company", company.getId(), before,
                snapshot(company)));
        return CompanyView.from(company);
    }

    private static BusinessRuleException duplicatedTaxId() {
        return new BusinessRuleException("DUPLICATE_TAX_ID", "The company tax id is already in use");
    }

    private static Map<String, Object> snapshot(Company company) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("legalName", company.getLegalName());
        values.put("tradeName", company.getTradeName());
        values.put("taxId", company.getTaxId());
        values.put("address", company.getAddress());
        values.put("phone", company.getPhone());
        values.put("email", company.getEmail());
        values.put("currency", company.getCurrency());
        values.put("timeZone", company.getTimeZone());
        return values;
    }
}
