package com.medinasystems.pos.organization.application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.organization.BranchPricingInfo;
import com.medinasystems.pos.organization.domain.Branch;
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
    public boolean exists(long branchId) {
        return branches.existsById(branchId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BranchInfo> info(long branchId) {
        return branches.findById(branchId).map(DefaultBranchDirectory::toInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(long branchId) {
        return branches.findById(branchId).map(Branch::isActive).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchInfo> activeBranches() {
        return branches.findByActiveTrueOrderByCodeAsc().stream().map(DefaultBranchDirectory::toInfo).toList();
    }

    private static BranchInfo toInfo(Branch branch) {
        return new BranchInfo(branch.getId(), branch.getName(), branch.getAddress(), branch.isAcceptsForeignReturns(),
                branch.getCode(), branch.isAllowNegativeStock());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BranchPricingInfo> find(long branchId) {
        return branches.findById(branchId).map(branch -> new BranchPricingInfo(branch.getId(),
                branch.getCountry().getId(), branch.getDefaultPriceListId()));
    }
}
