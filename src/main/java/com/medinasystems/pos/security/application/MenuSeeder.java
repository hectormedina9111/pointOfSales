package com.medinasystems.pos.security.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.security.domain.Privilege;
import com.medinasystems.pos.security.domain.PrivilegeType;
import com.medinasystems.pos.security.domain.Role;
import com.medinasystems.pos.security.infrastructure.PrivilegeRepository;
import com.medinasystems.pos.security.infrastructure.RoleRepository;

/**
 * Menu privileges of the frontend screens, central node only. Each menu is created when its code is missing,
 * and role ADMIN is made to hold it, so every startup is safe. A parent must come before its children in
 * {@link #MENUS}.
 *
 * <p>This is Java code and not a Flyway migration on purpose: a migration would insert rows with central-range
 * ids into every store database, and they would collide with the rows replicated from the central.
 */
@Component
@Profile("central")
@Order(5)
public class MenuSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MenuSeeder.class);
    private static final String ADMIN_ROLE = "ADMIN";

    /** One menu entry. The name is data in Spanish; the frontend translates it by its code. */
    record MenuDefinition(String code, String parentCode, String module, String name, String route, String icon,
                          int sortOrder) {
    }

    static final List<MenuDefinition> MENUS = List.of(
            new MenuDefinition("MENU_ORGANIZATION", null, "organization", "Organización", null, "building", 10),
            new MenuDefinition("MENU_ORGANIZATION_COUNTRIES", "MENU_ORGANIZATION", "organization", "Países",
                    "/organization/countries", "world", 1),
            new MenuDefinition("MENU_ORGANIZATION_COMPANY", "MENU_ORGANIZATION", "organization", "Empresa",
                    "/organization/company", "briefcase", 2),
            new MenuDefinition("MENU_ORGANIZATION_BRANCHES", "MENU_ORGANIZATION", "organization", "Sucursales",
                    "/organization/branches", "building-store", 3),
            new MenuDefinition("MENU_CATALOG", null, "catalog", "Catálogo", null, "box", 20),
            new MenuDefinition("MENU_CATALOG_PRODUCTS", "MENU_CATALOG", "catalog", "Productos",
                    "/catalog/products", "box", 1),
            new MenuDefinition("MENU_CATALOG_CATEGORIES", "MENU_CATALOG", "catalog", "Categorías",
                    "/catalog/categories", "category", 2),
            new MenuDefinition("MENU_CATALOG_UNITS", "MENU_CATALOG", "catalog", "Unidades de medida",
                    "/catalog/units", "ruler", 3),
            new MenuDefinition("MENU_CATALOG_TAXES", "MENU_CATALOG", "catalog", "Impuestos",
                    "/catalog/taxes", "receipt-tax", 4),
            new MenuDefinition("MENU_PRICING", null, "pricing", "Precios", null, "tag", 30),
            new MenuDefinition("MENU_PRICING_PRICE_LISTS", "MENU_PRICING", "pricing", "Listas de precios",
                    "/pricing/price-lists", "list", 1),
            new MenuDefinition("MENU_PRICING_PRICE_CHECK", "MENU_PRICING", "pricing", "Consultar precio",
                    "/pricing/price-check", "calculator", 2),
            new MenuDefinition("MENU_STORES", null, "sync", "Tiendas", null, "building-warehouse", 40),
            new MenuDefinition("MENU_STORES_NODES", "MENU_STORES", "sync", "Panel de tiendas",
                    "/stores/nodes", "layout-dashboard", 1),
            new MenuDefinition("MENU_STORES_SYNC_STATUS", "MENU_STORES", "sync", "Estado de sincronización",
                    "/stores/sync-status", "refresh", 2),
            new MenuDefinition("MENU_AUDIT", null, "audit", "Auditoría", "/audit", "history", 80));

    private final PrivilegeRepository privileges;
    private final RoleRepository roles;

    public MenuSeeder(PrivilegeRepository privileges, RoleRepository roles) {
        this.privileges = privileges;
        this.roles = roles;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role admin = roles.findByCode(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("Role ADMIN is missing; check migration V2"));
        int created = 0;
        for (MenuDefinition definition : MENUS) {
            Privilege menu = privileges.findByCode(definition.code()).orElse(null);
            if (menu == null) {
                menu = create(definition);
                created++;
            }
            admin.grant(menu);
        }
        log.info("Menu privileges created: {} of {}", created, MENUS.size());
    }

    private Privilege create(MenuDefinition definition) {
        Privilege parent = null;
        if (definition.parentCode() != null) {
            parent = privileges.findByCode(definition.parentCode())
                    .orElseThrow(() -> new IllegalStateException(
                            "Parent menu " + definition.parentCode() + " is missing; keep parents before children"));
        }
        Privilege menu = Privilege.catalog(PrivilegeType.MENU, definition.code(), definition.name(), null,
                definition.module());
        menu.update(definition.name(), null, parent, definition.route(), definition.icon(),
                definition.sortOrder(), true);
        return privileges.save(menu);
    }
}
