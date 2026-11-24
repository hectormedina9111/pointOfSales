package com.medinasystems.pos.organization.application;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.organization.BranchPricingInfo;
import com.medinasystems.pos.organization.infrastructure.BranchRepository;

/** Answers {@link BranchDirectory} from the branch table. */
@Component
public class DefaultBranchDirectory implements BranchDirectory {

    private final BranchRepository branches;

    public DefaultBranchDirectory(BranchRepository branches) {
        this.branches = branches;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BranchPricingInfo> find(long branchId) {
        return branches.findById(branchId).map(branch -> new BranchPricingInfo(branch.getId(),
                branch.getCountry().getId(), branch.getDefaultPriceListId()));
    }
}
