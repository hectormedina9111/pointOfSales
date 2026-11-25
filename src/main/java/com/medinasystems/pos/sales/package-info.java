/**
 * Sales: payment methods (master data of the central node) and the store's sales: quotes, holds, charges with their
 * payments, and tickets. Sales are documents of the store and are uploaded to the central node. Payment method codes
 * are fixed by the system (CASH, CARD, TRANSFER): they are seeded by the central node and cannot be created from the API.
 */
@ApplicationModule(displayName = "Sales")
package com.medinasystems.pos.sales;

import org.springframework.modulith.ApplicationModule;
