package com.medinasystems.pos.cash;

import java.util.Optional;

/**
 * Public API of the cash module for the sales module: the open session of a cashier, which is where a sale is
 * recorded. Sales never reads the cash tables directly.
 */
public interface OpenSessions {

    /** The cashier's open session, with its register and branch. Empty when the cashier has no open session. */
    Optional<OpenSessionInfo> currentFor(long userId);
}
