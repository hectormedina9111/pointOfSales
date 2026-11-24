package com.medinasystems.pos.shared;

import java.util.Optional;

/**
 * Branches this node can see. The central node sees all of them; a store sees only the branch the
 * central node assigned to it. Modules filter their queries with this rule, not with the branches of
 * the current user.
 */
public interface BranchScope {

    /** True when every branch is visible on this node, as on the central node. */
    boolean allBranches();

    /** The branch assigned to this node, when it has one. Empty on the central node and on a store without a branch. */
    Optional<Long> nodeBranchId();

    /** True when the branch is visible on this node. */
    default boolean canSee(long branchId) {
        return allBranches() || nodeBranchId().filter(id -> id == branchId).isPresent();
    }
}
