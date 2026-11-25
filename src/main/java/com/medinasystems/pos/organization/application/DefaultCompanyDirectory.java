package com.medinasystems.pos.organization.application;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.organization.CompanyDirectory;
import com.medinasystems.pos.organization.CompanyInfo;
import com.medinasystems.pos.organization.infrastructure.CompanyRepository;

/** Answers {@link CompanyDirectory} from the company table. */
@Component
public class DefaultCompanyDirectory implements CompanyDirectory {

    private final CompanyRepository companies;

    public DefaultCompanyDirectory(CompanyRepository companies) {
        this.companies = companies;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CompanyInfo> find() {
        return companies.findFirstByOrderByIdAsc().map(company -> new CompanyInfo(company.getLegalName(),
                company.getTradeName(), company.getTaxId(), company.getTimeZone()));
    }
}
