package com.medinasystems.pos.security.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.medinasystems.pos.security.domain.AppUser;
import com.medinasystems.pos.security.domain.Role;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** Creates access tokens (signed JWT) and opaque refresh tokens. */
@Service
public class TokenService {

    public static final String ISSUER = "pos";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final SecurityProperties.Jwt properties;
    private final NimbusJwtEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public TokenService(SecurityProperties securityProperties) {
        this.properties = securityProperties.jwt();
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(properties.secretKey()));
    }

    /** Access token claims: subject = username, "uid", "roles" (codes) and "pwd_change". */
    public String createAccessToken(AppUser user, Instant now) {
        List<String> roles = user.getRoles().stream().map(Role::getCode).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim("uid", user.getId())
                .claim("roles", roles)
                .claim("pwd_change", user.isMustChangePassword())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    public Duration refreshTokenTtl() {
        return properties.refreshTokenTtl();
    }

    /** 32 random bytes, Base64 URL encoded without padding. Only the hash is stored. */
    public String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 of a token, hex encoded (64 characters). */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
