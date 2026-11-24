package com.medinasystems.pos.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.medinasystems.pos.security.application.EndpointPrivileges;
import com.medinasystems.pos.security.infrastructure.PrivilegeRepository;

/**
 * Checks the ENDPOINT privilege of every /api/v1 route before its controller runs.
 * The route is the matched pattern, not the raw path, so "/users/42" and "/users/7" share one privilege.
 */
@Component
public class PrivilegeInterceptor implements HandlerInterceptor {

    private static final String HEAD = "HEAD";
    private static final String GET = "GET";

    private final PrivilegeRepository privileges;
    private final ProblemJsonWriter problems;

    public PrivilegeInterceptor(PrivilegeRepository privileges, ProblemJsonWriter problems) {
        this.privileges = privileges;
        this.problems = problems;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (matched == null) {
            return true;
        }
        String pattern = matched.toString();
        if (!EndpointPrivileges.requiresPrivilege(pattern)) {
            return true;
        }
        // Spring maps HEAD requests to the GET handler, so they need the GET privilege.
        String method = HEAD.equals(request.getMethod()) ? GET : request.getMethod();
        Long userId = currentUserId();
        if (userId != null && privileges.existsForUser(userId, method, pattern)) {
            return true;
        }
        problems.write(request, response, HttpStatus.FORBIDDEN, "title.FORBIDDEN",
                "error.MISSING_PRIVILEGE", method, pattern);
        return false;
    }

    private static Long currentUserId() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                && jwt.getToken().getClaim("uid") instanceof Number uid) {
            return uid.longValue();
        }
        return null;
    }
}
