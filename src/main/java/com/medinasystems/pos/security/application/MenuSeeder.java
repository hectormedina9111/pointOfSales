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
 * Menu privileges of the frontend screens, central node only. Each menu is created when its code is missing, and
 * an existing menu gets its name, parent, route, icon and order from {@link #MENUS} again, so a renamed menu reaches
 * existing databases. Its active flag is kept. Role ADMIN is made to hold every entry, so every startup is safe. A
 * parent must come before its children in {@link #MENUS}.
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

    /**
     * One menu or action entry. The name is data in Spanish; the frontend translates it by its code. ACTION
     * entries use the same record with no parent, route or icon, and sort order 0.
     */
    record MenuDefinition(String code, String parentCode, String module, String name, String route, String icon,
                          int sortOrder) {
    }

    /** Action privileges for buttons that need a manager's authorization. Granted to ADMIN, like the menus. */
    static final List<MenuDefinition> ACTIONS = List.of(
            new MenuDefinition("ACTION_SALE_DISCOUNT_OVERRIDE", null, "sales", "Autorizar descuento mayor al permitido",
                    null, null, 0),
            new MenuDefinition("ACTION_SALE_PRICE_OVERRIDE", null, "sales", "Autorizar cambio de precio", null, null, 0),
            new MenuDefinition("ACTION_SALE_RETURN", null, "sales", "Autorizar devolución", null, null, 0),
            new MenuDefinition("ACTION_CASH_WITHDRAWAL", null, "cash", "Autorizar retiro de efectivo", null, null, 0),
            new MenuDefinition("ACTION_SALES_VIEW_ALL", null, "sales", "Ver todas las ventas de la tienda", null,
                    null, 0));

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
            new MenuDefinition("MENU_INVENTORY", null, "inventory", "Inventario", null, "box", 25),
            new MenuDefinition("MENU_INVENTORY_STOCK", "MENU_INVENTORY", "inventory", "Existencias",
                    "/inventory/stock", "stack-2", 1),
            new MenuDefinition("MENU_INVENTORY_DOCUMENTS", "MENU_INVENTORY", "inventory",
                    "Documentos de inventario", "/inventory/documents", "file-text", 2),
            new MenuDefinition("MENU_INVENTORY_EQUIPMENT", "MENU_INVENTORY", "inventory", "Equipos",
                    "/inventory/equipment", "barcode", 3),
            new MenuDefinition("MENU_INVENTORY_EQUIPMENT_LOAD", "MENU_INVENTORY", "inventory", "Carga de equipos",
                    "/inventory/equipment-loads/new", "upload", 4),
            new MenuDefinition("MENU_INVENTORY_TRANSFERS", "MENU_INVENTORY", "inventory", "Transferencias",
                    "/inventory/transfers", "truck", 5),
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
            new MenuDefinition("MENU_SALES", null, "sales", "Ventas", null, "cash-register", 50),
            new MenuDefinition("MENU_SALES_POS", "MENU_SALES", "sales", "Punto de venta", "/sales/pos",
                    "shopping-cart", 1),
            new MenuDefinition("MENU_SALES_LIST", "MENU_SALES", "sales", "Órdenes", "/sales", "receipt", 2),
            new MenuDefinition("MENU_CREDIT_NOTES", "MENU_SALES", "sales", "Notas de crédito", "/credit-notes",
                    "receipt-refund", 3),
            new MenuDefinition("MENU_CASH_SESSION", "MENU_SALES", "cash", "Mi caja", "/cash/session", "cash", 4),
            new MenuDefinition("MENU_CASH_SESSIONS", "MENU_SALES", "cash", "Sesiones de caja", "/cash/sessions",
                    "list-check", 5),
            new MenuDefinition("MENU_SALES_SETTINGS", null, "sales", "Configuración de ventas", null, "settings", 55),
            new MenuDefinition("MENU_CASH_REGISTERS", "MENU_SALES_SETTINGS", "cash", "Cajas", "/cash/registers",
                    "device-desktop", 1),
            new MenuDefinition("MENU_PAYMENT_METHODS", "MENU_SALES_SETTINGS", "sales", "Métodos de pago",
                    "/sales/payment-methods", "credit-card", 2),
            new MenuDefinition("MENU_CUSTOMERS", null, "customer", "Clientes", "/customers", "users", 60),
            new MenuDefinition("MENU_AUDIT", null, "audit", "Auditoría", "/audit", "history", 80));

    /** Menus replaced by another one. Their privilege stays in the table, inactive, so the menu disappears. */
    static final List<String> RETIRED_MENUS = List.of("MENU_INVENTORY_SERIALS");

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
            created += seed(PrivilegeType.MENU, definition, admin);
        }
        for (MenuDefinition definition : ACTIONS) {
            created += seed(PrivilegeType.ACTION, definition, admin);
        }
        RETIRED_MENUS.forEach(this::retire);
        log.info("Menu and action privileges created: {} of {}", created, MENUS.size() + ACTIONS.size());
    }

    /** Deactivates a menu that is no longer offered. Nothing is deleted: the grants stay as they are. */
    private void retire(String code) {
        privileges.findByCode(code).filter(Privilege::isActive).ifPresent(privilege ->
                privilege.update(privilege.getName(), privilege.getDescription(), privilege.getParent(),
                        privilege.getFrontendRoute(), privilege.getIcon(), privilege.getSortOrder(), false));
    }

    /** Creates the privilege when its code is missing, and grants it to ADMIN. Returns 1 when it was created. */
    private int seed(PrivilegeType type, MenuDefinition definition, Role admin) {
        Privilege privilege = privileges.findByCode(definition.code()).orElse(null);
        int created = 0;
        if (privilege == null) {
            privilege = create(type, definition);
            created = 1;
        } else if (type == PrivilegeType.MENU) {
            // Only the placement is refreshed; setting the same values again changes nothing in the database.
            privilege.update(definition.name(), privilege.getDescription(), parentOf(definition),
                    definition.route(), definition.icon(), definition.sortOrder(), privilege.isActive());
        }
        admin.grant(privilege);
        return created;
    }

    private Privilege parentOf(MenuDefinition definition) {
        if (definition.parentCode() == null) {
            return null;
        }
        return privileges.findByCode(definition.parentCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Parent menu " + definition.parentCode() + " is missing; keep parents before children"));
    }

    private Privilege create(PrivilegeType type, MenuDefinition definition) {
        Privilege parent = parentOf(definition);
        Privilege menu = Privilege.catalog(type, definition.code(), definition.name(), null,
                definition.module());
        menu.update(definition.name(), null, parent, definition.route(), definition.icon(),
                definition.sortOrder(), true);
        return privileges.save(menu);
    }
}
