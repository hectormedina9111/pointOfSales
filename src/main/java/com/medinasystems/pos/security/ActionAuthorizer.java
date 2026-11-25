package com.medinasystems.pos.security;

/**
 * Public API of the security module for actions that need a manager's authorization, such as a cash withdrawal.
 * The clear password is checked here and never stored or logged.
 */
public interface ActionAuthorizer {

    /**
     * Privilege that lets a user see the sales and cash sessions of every cashier of the node. Without it, the lists
     * show only the user's own documents.
     */
    String SALES_VIEW_ALL_PRIVILEGE = "ACTION_SALES_VIEW_ALL";

    /**
     * Checks a user's credentials and privilege. The user must be active, not locked, assigned to a branch this node
     * serves, and must hold the privilege. A failed password counts toward the account lockout.
     *
     * @param username      user who authorizes the action
     * @param password      the user's clear password, checked against the stored hash
     * @param privilegeCode privilege the action needs, such as {@code ACTION_CASH_WITHDRAWAL}
     * @return id of the authorizing user
     * @throws com.medinasystems.pos.shared.BusinessRuleException code {@code AUTHORIZATION_DENIED} when any check fails
     */
    long authorize(String username, String password, String privilegeCode);

    /** True when the user holds the privilege through one of their roles. */
    boolean hasPrivilege(long userId, String privilegeCode);
}
