package com.medinasystems.pos.organization;

import java.util.Optional;

/**
 * Public API of the organization module for other modules. It answers questions about
 * branches without exposing the {@code Branch} entity.
 */
public interface BranchDirectory {

    /** The branch with what pricing needs from it. Empty when the branch does not exist. */
    Optional<BranchPricingInfo> find(long branchId);

    /** The name and address of the branch, for the tickets. Empty when the branch does not exist. */
    Optional<BranchInfo> info(long branchId);

    /** True when a branch with this id exists. */
    boolean exists(long branchId);
}
