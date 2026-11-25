package com.medinasystems.pos.security.application;

import java.security.SecureRandom;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.security.domain.AppUser;
import com.medinasystems.pos.security.infrastructure.AppUserRepository;
import com.medinasystems.pos.security.infrastructure.RoleRepository;

/**
 * Creates the initial "admin" user on the central node when it does not exist yet.
 * The password comes from {@code POS_ADMIN_INITIAL_PASSWORD}; if that is blank, a random
 * one is generated and logged once. The user must change it at the first sign-in.
 */
@Component
@Profile("central")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ADMIN_USERNAME = "admin";
    private static final String GENERATED_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int GENERATED_LENGTH = 20;

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AdminBootstrap(AppUserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                          SecurityProperties properties, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.findByUsername(ADMIN_USERNAME).isPresent()) {
            return;
        }
        String configured = properties.adminInitialPassword();
        boolean generated = configured == null || configured.isBlank();
        String password = generated ? generatePassword() : configured;

        AppUser admin = new AppUser(ADMIN_USERNAME, passwordEncoder.encode(password), "Administrador",
                clock.instant());
        admin.addRole(roles.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException("Role ADMIN is missing; check migration V2")));
        users.save(admin);

        if (generated) {
            log.warn("Initial admin password (shown once, change it at first login): {}", password);
        }
    }

    private String generatePassword() {
        return random.ints(GENERATED_LENGTH, 0, GENERATED_CHARS.length())
                .mapToObj(i -> String.valueOf(GENERATED_CHARS.charAt(i)))
                .reduce("", String::concat);
    }
}
