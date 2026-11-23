package com.medinasystems.pos.security.application;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.security.domain.Privilege;
import com.medinasystems.pos.security.domain.Role;
import com.medinasystems.pos.security.infrastructure.PrivilegeRepository;
import com.medinasystems.pos.security.infrastructure.RoleRepository;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/** Roles and the privileges assigned to them. */
@Service
@Transactional
public class RoleService {

    private final RoleRepository roles;
    private final PrivilegeRepository privileges;

    public RoleService(RoleRepository roles, PrivilegeRepository privileges) {
        this.roles = roles;
        this.privileges = privileges;
    }

    @Transactional(readOnly = true)
    public List<RoleView> list() {
        return roles.findAll(Sort.by("code")).stream().map(RoleView::from).toList();
    }

    @Transactional(readOnly = true)
    public RoleView get(Long id) {
        return RoleView.from(find(id));
    }

    public RoleView create(String code, String name, String description) {
        if (roles.findByCode(code).isPresent()) {
            throw new BusinessRuleException("role.code.duplicated", "The role code is already in use");
        }
        return RoleView.from(roles.save(new Role(code, name, description)));
    }

    public RoleView update(Long id, String name, String description) {
        Role role = find(id);
        role.update(name, description);
        return RoleView.from(role);
    }

    @Transactional(readOnly = true)
    public List<PrivilegeView> privileges(Long id) {
        return find(id).getPrivileges().stream()
                .sorted(Comparator.comparing(Privilege::getCode))
                .map(PrivilegeView::from)
                .toList();
    }

    /** Replaces all privileges of the role. Unknown codes are rejected and nothing changes. */
    public List<PrivilegeView> replacePrivileges(Long id, Collection<String> codes) {
        Role role = find(id);
        Set<String> wanted = Set.copyOf(codes);
        List<Privilege> found = privileges.findByCodeIn(wanted);
        if (found.size() != wanted.size()) {
            throw new BusinessRuleException("privilege.unknown", "One or more privilege codes do not exist");
        }
        role.replacePrivileges(new HashSet<>(found));
        return privileges(id);
    }

    private Role find(Long id) {
        return roles.findById(id).orElseThrow(() -> new ResourceNotFoundException("role", id));
    }
}
