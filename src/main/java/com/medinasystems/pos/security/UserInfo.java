package com.medinasystems.pos.security;

/** Identity of a user as other modules show it. */
public record UserInfo(long userId, String username, String fullName) {
}
