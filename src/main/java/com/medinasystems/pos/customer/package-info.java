/**
 * Customers: the people and companies that buy. The central node creates and changes them, and replicates them to
 * every store. A store can also create one: the customer is uploaded to the central node, which keeps the id the store
 * gave it. Other modules read customers through {@link com.medinasystems.pos.customer.CustomerDirectory}.
 */
@ApplicationModule(displayName = "Customer")
package com.medinasystems.pos.customer;

import org.springframework.modulith.ApplicationModule;
