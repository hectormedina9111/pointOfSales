/**
 * Inventory: the stock of each product in each branch, its movement log and the inventory documents (entries, exits and
 * counts). A store operates the inventory of its own branch and uploads its documents to the central node; the central
 * node operates the branches that no store serves. Other modules move stock only through
 * {@link com.medinasystems.pos.inventory.StockLedger}.
 */
@ApplicationModule(displayName = "Inventory")
package com.medinasystems.pos.inventory;

import org.springframework.modulith.ApplicationModule;
