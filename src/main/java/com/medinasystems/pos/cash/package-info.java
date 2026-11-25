/**
 * Cash: cash registers (points of sale) of each branch, and the cash sessions of the store that uses them. Registers
 * are master data created in the central node and replicated to the stores. Cash sessions and their movements are
 * store documents: they are created in the store and uploaded to the central node.
 */
@ApplicationModule(displayName = "Cash")
package com.medinasystems.pos.cash;

import org.springframework.modulith.ApplicationModule;
