package com.medinasystems.pos.sync;

import com.medinasystems.pos.shared.ServiceUnavailableException;

/**
 * The central node cannot be reached, or it refuses the store's credentials. Rendered as HTTP 503 with code
 * {@code CENTRAL_UNAVAILABLE} by the global exception handler.
 */
public class CentralUnavailableException extends ServiceUnavailableException {

    public CentralUnavailableException() {
        super("CENTRAL_UNAVAILABLE", "The central node cannot be reached");
    }
}
