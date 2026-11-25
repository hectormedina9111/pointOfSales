package com.medinasystems.pos.organization;

/** Name and address of a branch, as the sales tickets print them. */
public record BranchInfo(long branchId, String name, String address) {
}
