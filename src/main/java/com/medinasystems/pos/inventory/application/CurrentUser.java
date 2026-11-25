package com.medinasystems.pos.inventory.application;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The user id of the signed-in request, read from the {@code uid} claim of its token. */
final class CurrentUser {

    private CurrentUser() {
    }

    static long id() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                && jwt.getToken().getClaim("uid") instanceof Number uid) {
            return uid.longValue();
        }
        throw new IllegalStateException("The request has no signed-in user");
    }
}
