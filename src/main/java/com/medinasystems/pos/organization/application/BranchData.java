package com.medinasystems.pos.organization.application;

/** Input for creating or changing a branch. */
public record BranchData(String code, String name, String address, String phone, Long countryId,
                         boolean allowNegativeStock, boolean active, Long defaultPriceListId) {
}
