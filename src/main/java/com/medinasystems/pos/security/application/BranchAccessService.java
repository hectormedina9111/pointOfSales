package com.medinasystems.pos.security.application;

import java.util.Set;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.security.BranchAccess;
import com.medinasystems.pos.security.domain.AppUser;
import com.medinasystems.pos.security.infrastructure.AppUserRepository;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/** Implements the public {@link BranchAccess} API on top of the user_branch table. */
@Service
public class BranchAccessService implements BranchAccess {

    private final AppUserRepository users;

    public BranchAccessService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> branchIdsOf(long userId) {
        return Set.copyOf(find(userId).getBranchIds());
    }

    @Override
    @Transactional
    public void grant(long userId, long branchId) {
        find(userId).addBranch(branchId);
    }

    @Override
    public long currentUserId() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                && jwt.getToken().getClaim("uid") instanceof Number uid) {
            return uid.longValue();
        }
        throw new IllegalStateException("The request is not authenticated with a user token");
    }

    private AppUser find(long userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("user", userId));
    }
}
