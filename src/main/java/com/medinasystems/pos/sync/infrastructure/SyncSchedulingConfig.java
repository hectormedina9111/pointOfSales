package com.medinasystems.pos.sync.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Store side only: turns on the scheduled sync cycle in {@code StoreReplicator}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("store")
public class SyncSchedulingConfig {
}
