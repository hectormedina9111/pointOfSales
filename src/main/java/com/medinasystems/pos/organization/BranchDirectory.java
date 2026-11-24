package com.medinasystems.pos.organization;

import java.util.Optional;

/**
 * Public API of the organization module for other modules. It answers questions about
 * branches without exposing the {@code Branch} entity.
 */
public interface BranchDirectory {

    /** The branch with what pricing needs from it. Empty when the branch does not exist. */
    Optional<BranchPricingInfo> find(long branchId);
}
