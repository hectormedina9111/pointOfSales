package com.medinasystems.pos.shared;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Central node: every branch is visible. */
@Component
@Profile("central")
public class CentralBranchScope implements BranchScope {

    @Override
    public boolean allBranches() {
        return true;
    }

    @Override
    public Optional<Long> nodeBranchId() {
        return Optional.empty();
    }
}
