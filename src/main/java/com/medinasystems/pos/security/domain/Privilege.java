package com.medinasystems.pos.security.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;
import com.medinasystems.pos.shared.BusinessRuleException;

/** A permission: an endpoint, a menu entry or an action. The three kinds share one table. */
@Entity
@Table(name = "privilege")
public class Privilege extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PrivilegeType type;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(nullable = false, length = 30)
    private String module;

    @Column(name = "http_method", length = 7)
    private String httpMethod;

    @Column(name = "path_pattern", length = 200)
    private String pathPattern;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Privilege parent;

    @Column(name = "frontend_route", length = 200)
    private String frontendRoute;

    @Column(length = 50)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected Privilege() {
    }

    private Privilege(String code, PrivilegeType type, String name, String description, String module) {
        this.code = code;
        this.type = type;
        this.name = name;
        this.description = description;
        this.module = module;
    }

    /** An endpoint privilege. Created by the startup registrar, never by the API. */
    public static Privilege endpoint(String code, String httpMethod, String pathPattern, String name, String module) {
        Privilege privilege = new Privilege(code, PrivilegeType.ENDPOINT, name, null, module);
        privilege.httpMethod = httpMethod;
        privilege.pathPattern = pathPattern;
        return privilege;
    }

    /** A menu or action privilege created from the administration screens. Endpoints are rejected. */
    public static Privilege catalog(PrivilegeType type, String code, String name, String description, String module) {
        if (type == PrivilegeType.ENDPOINT) {
            throw new BusinessRuleException("privilege.endpoint.read_only",
                    "Endpoint privileges are registered automatically and cannot be created");
        }
        return new Privilege(code, type, name, description, module);
    }

    /**
     * Edits the privilege. For ENDPOINT rows only name, description and active change;
     * the menu placement arguments are ignored. A parent must be a MENU privilege other than itself.
     */
    public void update(String name, String description, Privilege newParent, String frontendRoute,
                       String icon, int sortOrder, boolean active) {
        if (type != PrivilegeType.ENDPOINT) {
            if (newParent != null && (newParent.getType() != PrivilegeType.MENU
                    || newParent.getId().equals(getId()))) {
                throw new BusinessRuleException("privilege.parent.invalid",
                        "The parent must be a menu privilege different from this one");
            }
            this.parent = newParent;
            this.frontendRoute = frontendRoute;
            this.icon = icon;
            this.sortOrder = (short) sortOrder;
        }
        this.name = name;
        this.description = description;
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public PrivilegeType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getModule() {
        return module;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public Privilege getParent() {
        return parent;
    }

    public String getFrontendRoute() {
        return frontendRoute;
    }

    public String getIcon() {
        return icon;
    }

    public short getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
