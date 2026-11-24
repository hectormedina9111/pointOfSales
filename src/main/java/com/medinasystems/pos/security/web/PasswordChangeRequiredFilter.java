package com.medinasystems.pos.security.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * While the token carries {@code pwd_change=true}, only the endpoints needed to change
 * the password (and sign out) are allowed. Not a Spring bean: it is added to the chain
 * in {@link SecurityConfig}.
 */
final class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private final ProblemJsonWriter problems;

    PasswordChangeRequiredFilter(ProblemJsonWriter problems) {
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (mustChangePassword() && !isAllowedWhilePending(request)) {
            problems.write(request, response, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED", "title.PASSWORD_CHANGE_REQUIRED",
                    "error.PASSWORD_CHANGE_REQUIRED");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean mustChangePassword() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                && Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean("pwd_change"));
    }

    private static boolean isAllowedWhilePending(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return ("GET".equals(method) && "/api/v1/me".equals(path))
                || ("POST".equals(method) && ("/api/v1/me/password".equals(path)
                        || "/api/v1/auth/logout".equals(path)));
    }
}
