package com.medinasystems.pos.security.application;

import java.util.List;
import java.util.Locale;

/**
 * Rules shared by the startup registrar and the request interceptor: which routes need an
 * ENDPOINT privilege, and how each privilege is named.
 */
public final class EndpointPrivileges {

    static final String PREFIX = "/api/v1/";
    static final int CODE_MAX = 80;
    static final int NAME_MAX = 120;
    static final int MODULE_MAX = 30;

    /** Public and self-service routes: they never need a privilege. */
    private static final List<String> EXEMPT = List.of("/api/v1/auth", "/api/v1/system", "/api/v1/me");

    private EndpointPrivileges() {
    }

    /** True for routes under /api/v1 that need an ENDPOINT privilege. */
    public static boolean requiresPrivilege(String pattern) {
        if (pattern == null || !pattern.startsWith(PREFIX)) {
            return false;
        }
        return EXEMPT.stream().noneMatch(prefix -> pattern.equals(prefix) || pattern.startsWith(prefix + "/"));
    }

    /** For example GET + /api/v1/users/{id}/privileges gives GET_USERS_ID_PRIVILEGES. */
    static String code(String httpMethod, String pattern) {
        String body = pattern.substring(PREFIX.length()).toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return truncate(httpMethod + "_" + body, CODE_MAX);
    }

    /** The first path segment after /api/v1/, for example "users". */
    static String module(String pattern) {
        String rest = pattern.substring(PREFIX.length());
        return truncate(rest.split("/")[0], MODULE_MAX);
    }

    static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
