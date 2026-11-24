/**
 * Sync: store registration and activation, store credentials, and the id ranges of each store.
 * The central node registers stores and serves their activation codes; a store activates itself
 * against the central node and adjusts its sequences to its own range. Data replication comes later.
 */
@ApplicationModule(displayName = "Sync")
package com.medinasystems.pos.sync;

import org.springframework.modulith.ApplicationModule;
