package com.medinasystems.pos.organization;

/**
 * What pricing needs about a branch: its country and its default price list, or null when it has none.
 */
public record BranchPricingInfo(long branchId, long countryId, Long defaultPriceListId) {
}
