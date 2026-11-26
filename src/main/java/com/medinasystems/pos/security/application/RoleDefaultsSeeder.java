package com.medinasystems.pos.security.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.security.domain.Privilege;
import com.medinasystems.pos.security.domain.Role;
import com.medinasystems.pos.security.domain.RoleDefaultGrant;
import com.medinasystems.pos.security.domain.RoleDefaultGrantKey;
import com.medinasystems.pos.security.infrastructure.PrivilegeRepository;
import com.medinasystems.pos.security.infrastructure.RoleDefaultGrantRepository;
import com.medinasystems.pos.security.infrastructure.RoleRepository;

/**
 * Initial privileges of the cashier and manager roles, central node only. Each (role, privilege) pair is applied
 * once: when it is granted, a row is recorded in {@code role_default_grant}, and a pair with a row is never
 * granted again. So a grant that an administrator removes stays removed after restarts.
 *
 * <p>A privilege code that does not exist yet is skipped WITHOUT recording the pair, so it is applied on the first
 * startup in which the code exists. Order 40: it runs after {@link MenuSeeder} (5) and
 * {@link EndpointPrivilegeRegistrar} (30), which create the codes listed here.
 */
@Component
@Profile("central")
@Order(40)
public class RoleDefaultsSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleDefaultsSeeder.class);

    private static final List<String> CASHIER_PRIVILEGES = List.of(
            "MENU_SALES", "MENU_SALES_POS", "MENU_SALES_LIST", "MENU_CASH_SESSION",
            "GET_CASH_REGISTERS", "GET_PAYMENT_METHODS",
            "GET_PRODUCTS", "GET_PRODUCTS_ID", "GET_PRODUCTS_BARCODE_CODE", "GET_PRODUCTS_ID_PRICE",
            "GET_CATEGORIES", "GET_CATEGORIES_ID_ATTRIBUTES",
            "POST_CASH_SESSIONS", "GET_CASH_SESSIONS_CURRENT", "GET_CASH_SESSIONS_ID",
            "POST_CASH_SESSIONS_ID_MOVEMENTS", "GET_CASH_SESSIONS_ID_SUMMARY", "POST_CASH_SESSIONS_ID_CLOSE",
            "POST_SALES_QUOTE", "POST_SALES_HOLDS", "GET_SALES_HOLDS", "GET_SALES_HOLDS_ID", "DELETE_SALES_HOLDS_ID",
            "PUT_SALES_HOLDS_ID", "POST_SALES_HOLDS_ID_CHECKOUT",
            "POST_SALES", "GET_SALES_ID", "GET_SALES_ID_TICKET",
            "GET_SALES_ID_RETURNABLE", "POST_SALES_ID_RETURN_QUOTE", "POST_SALES_ID_RETURNS", "GET_SALES_RETURNABLE_LOOKUP",
            "MENU_CREDIT_NOTES", "GET_CREDIT_NOTES", "GET_CREDIT_NOTES_ID", "GET_CREDIT_NOTES_LOOKUP",
            "MENU_CUSTOMERS", "GET_CUSTOMERS", "GET_CUSTOMERS_ID", "POST_CUSTOMERS",
            // The sale screen shows the stock of a product and asks for its serial numbers: read only.
            "GET_INVENTORY", "GET_INVENTORY_PRODUCTID_BRANCHES_BRANCHID", "GET_INVENTORY_AVAILABILITY",
            "MENU_INVENTORY", "MENU_INVENTORY_SERIALS", "GET_PRODUCTS_ID_SERIALS", "GET_SERIALS",
            // The lists show the cashier's own documents: see ACTION_SALES_VIEW_ALL in the sales and cash services.
            "GET_SALES", "GET_CASH_SESSIONS");

    private static final List<String> WAREHOUSE_CLERK_PRIVILEGES = List.of(
            "MENU_INVENTORY", "MENU_INVENTORY_STOCK", "MENU_INVENTORY_DOCUMENTS", "MENU_INVENTORY_SERIALS",
            "GET_INVENTORY", "GET_INVENTORY_PRODUCTID_BRANCHES_BRANCHID", "GET_INVENTORY_AVAILABILITY",
            "GET_INVENTORY_MOVEMENTS", "GET_PRODUCTS_ID_SERIALS", "GET_SERIALS",
            "GET_INVENTORY_DOCUMENTS", "GET_INVENTORY_DOCUMENTS_ID", "POST_INVENTORY_DOCUMENTS",
            "MENU_INVENTORY_TRANSFERS", "GET_INVENTORY_TRANSFERS", "GET_INVENTORY_TRANSFERS_ID",
            "POST_INVENTORY_TRANSFERS", "POST_INVENTORY_TRANSFERS_ID_RECEIPT", "GET_INVENTORY_TRANSFER_BRANCHES",
            "GET_PRODUCTS", "GET_PRODUCTS_ID", "GET_CATEGORIES", "GET_CATEGORIES_ID_ATTRIBUTES", "GET_BRANCHES");

    private static final List<String> MANAGER_PRIVILEGES = Stream.of(CASHIER_PRIVILEGES,
            WAREHOUSE_CLERK_PRIVILEGES, List.of(
            "MENU_CASH_SESSIONS", "ACTION_SALE_DISCOUNT_OVERRIDE", "ACTION_SALE_PRICE_OVERRIDE", "ACTION_SALE_RETURN",
            "ACTION_CASH_WITHDRAWAL",
            "GET_CASH_REGISTERS_ID",
            "ACTION_SALES_VIEW_ALL", "GET_CREDIT_NOTES_ID_REDEMPTIONS"))
            .flatMap(List::stream)
            .distinct()
            .toList();

    /** Role code to the privilege codes it starts with. */
    static final Map<String, List<String>> DEFAULT_GRANTS = Map.of(
            "CASHIER", CASHIER_PRIVILEGES,
            "WAREHOUSE_CLERK", WAREHOUSE_CLERK_PRIVILEGES,
            "MANAGER", MANAGER_PRIVILEGES);

    private final RoleRepository roles;
    private final PrivilegeRepository privileges;
    private final RoleDefaultGrantRepository appliedGrants;

    public RoleDefaultsSeeder(RoleRepository roles, PrivilegeRepository privileges,
                              RoleDefaultGrantRepository appliedGrants) {
        this.roles = roles;
        this.privileges = privileges;
        this.appliedGrants = appliedGrants;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int added = 0;
        for (Map.Entry<String, List<String>> entry : DEFAULT_GRANTS.entrySet()) {
            String roleCode = entry.getKey();
            Role role = roles.findByCode(roleCode)
                    .orElseThrow(() -> new IllegalStateException("Role " + roleCode + " is missing; check migration V2"));
            for (String privilegeCode : entry.getValue()) {
                if (appliedGrants.existsById(new RoleDefaultGrantKey(roleCode, privilegeCode))) {
                    continue;
                }
                Privilege privilege = privileges.findByCode(privilegeCode).orElse(null);
                if (privilege == null) {
                    // Not recorded: the pair is applied once the code exists.
                    continue;
                }
                if (!role.getPrivileges().contains(privilege)) {
                    role.grant(privilege);
                    added++;
                }
                appliedGrants.save(new RoleDefaultGrant(roleCode, privilegeCode, Instant.now()));
            }
        }
        log.info("Default role grants added: {}", added);
    }
}
