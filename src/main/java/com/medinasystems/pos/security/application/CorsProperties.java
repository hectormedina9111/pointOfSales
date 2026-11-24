package com.medinasystems.pos.security.application;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cross-origin settings bound from {@code pos.cors.*}. The web frontend of each
 * node is served from its own origin and calls this node's API directly.
 *
 * @param allowedOrigins origins allowed to call the API; empty disables CORS
 */
@ConfigurationProperties("pos.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
    }
}
