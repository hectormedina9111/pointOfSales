package com.medinasystems.pos.sales.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.sales.domain.PaymentMethod;
import com.medinasystems.pos.sales.infrastructure.PaymentMethodRepository;

/**
 * Payment methods with fixed codes, central node only. A code is created only when it is missing, so every
 * startup is safe; names and flags of existing rows are not overwritten, since the central node may have
 * changed them.
 *
 * <p>Java code and not a Flyway migration, for the same reason as the menus: rows with central-range ids
 * must not be written into the store databases by a migration.
 */
@Component
@Profile("central")
public class PaymentMethodSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodSeeder.class);

    /** One fixed payment method. The name is data in Spanish, like the menu names. */
    record Seed(String code, String name, boolean requiresReference, boolean allowsChange) {
    }

    /** CREDIT_NOTE pays with the balance of a credit note: no reference, and no change. */
    static final List<Seed> SEEDS = List.of(
            new Seed("CASH", "Efectivo", false, true),
            new Seed("CARD", "Tarjeta", true, false),
            new Seed("TRANSFER", "Transferencia", true, false),
            new Seed("CREDIT_NOTE", "Nota de crédito", false, false));

    private final PaymentMethodRepository methods;

    public PaymentMethodSeeder(PaymentMethodRepository methods) {
        this.methods = methods;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (Seed seed : SEEDS) {
            if (!methods.existsByCode(seed.code())) {
                methods.save(new PaymentMethod(seed.code(), seed.name(), seed.requiresReference(),
                        seed.allowsChange(), true));
                created++;
            }
        }
        log.info("Payment methods created: {} of {}", created, SEEDS.size());
    }
}
