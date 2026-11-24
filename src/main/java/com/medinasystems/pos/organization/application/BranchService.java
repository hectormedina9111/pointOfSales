package com.medinasystems.pos.organization.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.organization.domain.Branch;
import com.medinasystems.pos.organization.domain.Company;
import com.medinasystems.pos.organization.domain.Country;
import com.medinasystems.pos.organization.infrastructure.BranchRepository;
import com.medinasystems.pos.organization.infrastructure.CompanyRepository;
import com.medinasystems.pos.organization.infrastructure.CountryRepository;
import com.medinasystems.pos.security.BranchAccess;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Branches. Every read and change is limited to the branches the current user can see;
 * a branch outside that set is reported as not found, so its existence is not revealed.
 */
@Service
@Transactional
public class BranchService {

    private final BranchRepository branches;
    private final CountryRepository countries;
    private final CompanyRepository companies;
    private final BranchAccess access;
    private final ApplicationEventPublisher events;

    public BranchService(BranchRepository branches, CountryRepository countries, CompanyRepository companies,
                         BranchAccess access, ApplicationEventPublisher events) {
        this.branches = branches;
        this.countries = countries;
        this.companies = companies;
        this.access = access;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<BranchView> list(Pageable pageable) {
        Set<Long> visible = visibleIds();
        if (visible.isEmpty()) {
            return Page.empty(pageable);
        }
        return branches.findByIdIn(visible, pageable).map(BranchView::from);
    }

    @Transactional(readOnly = true)
    public BranchView get(Long id) {
        return BranchView.from(visible(id));
    }

    public BranchView create(BranchData data) {
        Company company = companies.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new BusinessRuleException("COMPANY_NOT_CONFIGURED",
                        "Configure the company before creating branches"));
        Country country = findCountry(data.countryId());
        if (branches.existsByCode(data.code())) {
            throw duplicatedCode();
        }
        Branch branch = new Branch(company, country, data.code(), data.name(), data.address(), data.phone(),
                data.allowNegativeStock(), data.active(), data.defaultPriceListId());
        Branch saved = saveChecking(branch, data.defaultPriceListId());
        events.publishEvent(AuditEvent.created("BRANCH_CREATED", "branch", saved.getId(), snapshot(saved)));
        return BranchView.from(saved);
    }

    public BranchView update(Long id, BranchData data) {
        Branch branch = visible(id);
        Map<String, Object> before = snapshot(branch);
        Country country = findCountry(data.countryId());
        if (branches.existsByCodeAndIdNot(data.code(), id)) {
            throw duplicatedCode();
        }
        branch.update(country, data.code(), data.name(), data.address(), data.phone(),
                data.allowNegativeStock(), data.active(), data.defaultPriceListId());
        saveChecking(branch, data.defaultPriceListId());
        events.publishEvent(AuditEvent.updated("BRANCH_UPDATED", "branch", id, before, snapshot(branch)));
        return BranchView.from(branch);
    }

    /**
     * Flushes the branch now so the foreign key of {@code default_price_list_id} is checked here.
     * A missing price list is reported as PRICE_LIST_NOT_FOUND; other violations are not translated.
     */
    private Branch saveChecking(Branch branch, Long defaultPriceListId) {
        try {
            return branches.saveAndFlush(branch);
        } catch (DataIntegrityViolationException ex) {
            if (defaultPriceListId == null) {
                throw ex;
            }
            throw new BusinessRuleException("PRICE_LIST_NOT_FOUND", "The default price list does not exist");
        }
    }

    private Set<Long> visibleIds() {
        return access.branchIdsOf(access.currentUserId());
    }

    private Branch visible(Long id) {
        if (!visibleIds().contains(id)) {
            throw new ResourceNotFoundException("branch", id);
        }
        return branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("branch", id));
    }

    private Country findCountry(Long countryId) {
        return countries.findById(countryId)
                .orElseThrow(() -> new BusinessRuleException("COUNTRY_NOT_FOUND", "The country does not exist"));
    }

    private static BusinessRuleException duplicatedCode() {
        return new BusinessRuleException("DUPLICATE_BRANCH_CODE", "The branch code is already in use");
    }

    private static Map<String, Object> snapshot(Branch branch) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", branch.getCode());
        values.put("name", branch.getName());
        values.put("address", branch.getAddress());
        values.put("phone", branch.getPhone());
        values.put("countryId", branch.getCountry().getId());
        values.put("allowNegativeStock", branch.isAllowNegativeStock());
        values.put("active", branch.isActive());
        values.put("defaultPriceListId", branch.getDefaultPriceListId());
        return values;
    }
}
