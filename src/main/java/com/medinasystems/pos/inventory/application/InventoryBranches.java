package com.medinasystems.pos.inventory.application;

import org.springframework.stereotype.Component;

import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.shared.ResourceNotFoundException;
import com.medinasystems.pos.sync.StoreBranches;

/**
 * Which node operates the inventory of a branch. A store operates only its own branch. The central node operates the
 * branches that no store serves, such as a central warehouse. Reads are not limited by this rule: every node shows the
 * branches it sees (see {@link BranchScope}).
 */
@Component
public class InventoryBranches {

    private final BranchDirectory branches;
    private final BranchScope scope;
    private final NodeProperties node;
    private final StoreBranches storeBranches;

    public InventoryBranches(BranchDirectory branches, BranchScope scope, NodeProperties node, StoreBranches storeBranches) {
        this.branches = branches;
        this.scope = scope;
        this.node = node;
        this.storeBranches = storeBranches;
    }

    /**
     * The branch, when this node operates its inventory.
     *
     * @throws ResourceNotFoundException     when the branch does not exist
     * @throws BusinessRuleException         with code {@code INVENTORY_BRANCH_NOT_OPERATED} when another node operates it
     */
    BranchInfo operated(long branchId) {
        BranchInfo info = branches.info(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("branch", branchId));
        if (!operates(branchId)) {
            throw new BusinessRuleException("INVENTORY_BRANCH_NOT_OPERATED",
                    "This node does not operate the inventory of the branch");
        }
        return info;
    }

    boolean operates(long branchId) {
        return node.isStore() ? scope.canSee(branchId) : !storeBranches.isServedByStore(branchId);
    }
}
