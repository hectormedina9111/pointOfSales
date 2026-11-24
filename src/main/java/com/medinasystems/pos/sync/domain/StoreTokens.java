package com.medinasystems.pos.sync.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Random secrets for store activation and the SHA-256 hashes stored in place of them.
 * Plain SHA-256 is enough here: the secrets are random and long, so they cannot be guessed from the hash.
 */
public final class StoreTokens {

    /** Upper-case letters and digits without the look-alikes I, L, O, 0 and 1. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int ACTIVATION_CODE_LENGTH = 16;
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private StoreTokens() {
    }

    /** A new one-time activation code, 16 characters from a 31-character alphabet. */
    public static String newActivationCode() {
        StringBuilder code = new StringBuilder(ACTIVATION_CODE_LENGTH);
        for (int i = 0; i < ACTIVATION_CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    /** A new permanent store token: 32 random bytes in base64url, without padding. */
    public static String newStoreToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 of the value, in lower-case hexadecimal (64 characters). */
    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", ex);
        }
    }
}
