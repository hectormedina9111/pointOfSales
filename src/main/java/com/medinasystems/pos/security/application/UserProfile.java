package com.medinasystems.pos.security.application;

import java.util.List;

/** The signed-in user as shown to the client. */
public record UserProfile(Long id, String username, String fullName, List<String> roles, boolean mustChangePassword) {
}
