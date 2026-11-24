package com.medinasystems.pos.sync.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Central node only: turns on the scheduled log purge in {@code SyncLogPurger}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("central")
public class CentralSchedulingConfig {
}
