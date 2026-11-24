package com.medinasystems.pos.sync.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.medinasystems.pos.sync.infrastructure.StoreNodeRepository;

/**
 * Registers the store-token filter for /api/v1/sync/*. It runs before the Spring Security chain, which
 * already lets these routes through without a user token.
 */
@Configuration(proxyBeanMethods = false)
class SyncWebConfig {

    @Bean
    FilterRegistrationBean<StoreTokenAuthenticationFilter> storeTokenFilter(StoreNodeRepository nodes,
                                                                            StoreProblemWriter problems) {
        FilterRegistrationBean<StoreTokenAuthenticationFilter> registration =
                new FilterRegistrationBean<>(new StoreTokenAuthenticationFilter(nodes, problems));
        registration.addUrlPatterns("/api/v1/sync/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
        return registration;
    }
}
