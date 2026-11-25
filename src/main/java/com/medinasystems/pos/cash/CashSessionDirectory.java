package com.medinasystems.pos.cash;

/**
 * Public API of the cash module for other modules. Lets the sales module check that a cash session exists without
 * reading the cash tables.
 */
public interface CashSessionDirectory {

    /** True when a cash session with that id is stored on this node. */
    boolean exists(long cashSessionId);
}
