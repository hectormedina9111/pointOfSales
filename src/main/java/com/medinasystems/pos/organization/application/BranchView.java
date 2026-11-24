package com.medinasystems.pos.organization.application;

import com.medinasystems.pos.organization.domain.Branch;

public record BranchView(Long id, Long companyId, Long countryId, String code, String name, String address,
                         String phone, boolean allowNegativeStock, boolean active, Long defaultPriceListId) {

    public static BranchView from(Branch branch) {
        return new BranchView(branch.getId(), branch.getCompany().getId(), branch.getCountry().getId(),
                branch.getCode(), branch.getName(), branch.getAddress(), branch.getPhone(),
                branch.isAllowNegativeStock(), branch.isActive(), branch.getDefaultPriceListId());
    }
}
