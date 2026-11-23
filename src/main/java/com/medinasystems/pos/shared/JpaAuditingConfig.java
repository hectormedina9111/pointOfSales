package com.medinasystems.pos.shared;

import java.time.Clock;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Fills the audit columns. The auditor is the JWT subject of the current request,
 * or {@code system} when there is none (startup, anonymous login attempts).
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "dateTimeProvider")
public class JpaAuditingConfig {

    static final String SYSTEM_AUDITOR = "system";

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                ? Optional.of(jwt.getName())
                : Optional.of(SYSTEM_AUDITOR);
    }

    @Bean
    DateTimeProvider dateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
