package com.medinasystems.pos.security.application;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;
import com.medinasystems.pos.security.domain.Privilege;
import com.medinasystems.pos.security.domain.PrivilegeType;
import com.medinasystems.pos.security.infrastructure.PrivilegeRepository;

/** Maintains the privilege catalog and builds what the signed-in user sees: menus and codes. */
@Service
@Transactional
public class PrivilegeService {

    private static final Sort ORDER = Sort.by("module", "sortOrder", "code");
    private static final List<String> CODE_TYPES = List.of(PrivilegeType.ENDPOINT.name(),
            PrivilegeType.ACTION.name());

    private final PrivilegeRepository privileges;

    public PrivilegeService(PrivilegeRepository privileges) {
        this.privileges = privileges;
    }

    /** Placement in the menu. Ignored for ENDPOINT privileges. */
    public record Placement(String parentCode, String frontendRoute, String icon, int sortOrder) {
    }

    @Transactional(readOnly = true)
    public List<PrivilegeView> list(PrivilegeType type) {
        List<Privilege> found = type == null ? privileges.findAll(ORDER) : privileges.findByType(type, ORDER);
        return found.stream().map(PrivilegeView::from).toList();
    }

    public PrivilegeView create(PrivilegeType type, String code, String name, String description, String module,
                                Placement placement) {
        if (privileges.findByCode(code).isPresent()) {
            throw new BusinessRuleException("privilege.code.duplicated", "The privilege code is already in use");
        }
        Privilege privilege = Privilege.catalog(type, code, name, description, module);
        privilege.update(name, description, parentOf(placement.parentCode()), placement.frontendRoute(),
                placement.icon(), placement.sortOrder(), true);
        return PrivilegeView.from(privileges.save(privilege));
    }

    public PrivilegeView update(Long id, String name, String description, Placement placement, boolean active) {
        Privilege privilege = privileges.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("privilege", id));
        privilege.update(name, description, parentOf(placement.parentCode()), placement.frontendRoute(),
                placement.icon(), placement.sortOrder(), active);
        return PrivilegeView.from(privilege);
    }

    /**
     * The menu tree the user may see. A menu appears only when it is granted; a child appears
     * only when it and its parent are both granted.
     */
    @Transactional(readOnly = true)
    public List<MenuItem> menusFor(Long userId) {
        List<Privilege> granted = privileges.findActiveForUser(userId, PrivilegeType.MENU);
        Map<Long, List<Privilege>> childrenByParent = granted.stream()
                .filter(p -> p.getParent() != null)
                .collect(Collectors.groupingBy(p -> p.getParent().getId()));
        return granted.stream()
                .filter(p -> p.getParent() == null)
                .map(p -> toItem(p, childrenByParent))
                .toList();
    }

    /** Codes of the ENDPOINT and ACTION privileges the user holds. */
    @Transactional(readOnly = true)
    public List<String> codesFor(Long userId) {
        return privileges.findCodesForUser(userId, CODE_TYPES);
    }

    private static MenuItem toItem(Privilege privilege, Map<Long, List<Privilege>> childrenByParent) {
        List<MenuItem> children = childrenByParent.getOrDefault(privilege.getId(), List.of()).stream()
                .map(child -> toItem(child, childrenByParent))
                .toList();
        return new MenuItem(privilege.getCode(), privilege.getName(), privilege.getFrontendRoute(),
                privilege.getIcon(), children);
    }

    private Privilege parentOf(String parentCode) {
        if (parentCode == null || parentCode.isBlank()) {
            return null;
        }
        return privileges.findByCode(parentCode)
                .orElseThrow(() -> new BusinessRuleException("privilege.parent.unknown",
                        "The parent privilege does not exist"));
    }
}
