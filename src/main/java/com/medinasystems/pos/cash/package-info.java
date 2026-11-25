/**
 * Cash: cash registers (points of sale) of each branch. Registers are master data created in the central
 * node and replicated to the stores. Cash sessions and movements will live here too, store side.
 */
@ApplicationModule(displayName = "Cash")
package com.medinasystems.pos.cash;

import org.springframework.modulith.ApplicationModule;
