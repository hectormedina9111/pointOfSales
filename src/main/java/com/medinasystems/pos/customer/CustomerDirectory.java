package com.medinasystems.pos.customer;

import java.util.Optional;

/**
 * Public API of the customer module for other modules, such as sales. A sale names its customer by id and reads the
 * customer through this API, without touching the customer tables.
 */
public interface CustomerDirectory {

    /** Id of the final consumer, the customer used when no other one is named. It is seeded by migration V16. */
    long FINAL_CONSUMER_ID = 1L;

    /** The customer with that id, or empty when it does not exist on this node. */
    Optional<CustomerInfo> find(long id);
}
