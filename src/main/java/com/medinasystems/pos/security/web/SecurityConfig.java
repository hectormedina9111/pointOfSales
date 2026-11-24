package com.medinasystems.pos.security.web;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.medinasystems.pos.security.application.CorsProperties;
import com.medinasystems.pos.security.application.SecurityProperties;
import com.medinasystems.pos.security.application.TokenService;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                            ProblemJsonWriter problems, CorsProperties corsProperties,
                                            CorsConfigurationSource corsConfigurationSource) throws Exception {
        if (corsProperties.allowedOrigins().isEmpty()) {
            http.cors(cors -> cors.disable());
        } else {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        }
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/system/info", "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**", "/actuator/health/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // CORS preflight requests carry no credentials; the browser sends them before the real call.
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        // Sync routes: the status route needs a user token; the rest are authenticated
                        // by the store token in StoreTokenAuthenticationFilter, or are public (activation).
                        .requestMatchers(HttpMethod.GET, "/api/v1/sync/status").authenticated()
                        .requestMatchers("/api/v1/sync/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, ex) -> unauthorized(problems, request, response)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) -> unauthorized(problems, request, response))
                        .accessDeniedHandler((request, response, ex) -> forbidden(problems, request, response)))
                .addFilterAfter(new PasswordChangeRequiredFilter(problems), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** CORS rules for {@code /api/**}; only used when {@code pos.cors.allowed-origins} is set. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Accept-Language",
                "X-Store-Token"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.jwt().secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TokenService.ISSUER));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Roles in the "roles" claim become authorities "ROLE_&lt;code&gt;". */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static void unauthorized(ProblemJsonWriter problems, HttpServletRequest request,
                                     HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        problems.write(request, response, HttpStatus.UNAUTHORIZED, "title.UNAUTHENTICATED",
                "error.UNAUTHENTICATED");
    }

    private static void forbidden(ProblemJsonWriter problems, HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {
        problems.write(request, response, HttpStatus.FORBIDDEN, "title.FORBIDDEN", "error.ACCESS_DENIED");
    }
}
