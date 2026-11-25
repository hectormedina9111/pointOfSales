package com.medinasystems.pos.organization;

/**
 * Identity and rules of a branch as other modules show them. {@code code} is the short code that numbers its documents;
 * {@code allowNegativeStock} tells whether it may sell or move stock it does not have.
 */
public record BranchInfo(long branchId, String name, String address, boolean acceptsForeignReturns, String code,
                         boolean allowNegativeStock) {
}
