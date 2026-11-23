package com.medinasystems.pos.security.application;

/**
 * Result of a successful login or refresh.
 *
 * @param expiresIn          access token lifetime in seconds
 * @param mustChangePassword whether the client must send the user to the password change screen
 */
public record TokenPair(String accessToken, String refreshToken, long expiresIn, boolean mustChangePassword) {
}
