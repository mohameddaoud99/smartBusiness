package com.sales.smartBusiness.security;

import com.sales.smartBusiness.platform.PlatformAdmin;
import com.sales.smartBusiness.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and reads the access token.
 * <p>
 * A company-user token carries the user id and the company id; a platform-admin token
 * carries the admin id and a {@code platform} claim instead. Permissions are
 * deliberately left out of both: they are read from the database on every request so
 * that a revoked role — or a revoked platform admin — cannot survive inside a token
 * that is still valid.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(@Value("${smartbusiness.jwt.secret}") String secret,
                      @Value("${smartbusiness.jwt.expiration}") Duration expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String generate(User user) {
        return build(String.valueOf(user.getId()))
                .claim("companyId", user.getCompany().getId())
                .compact();
    }

    public String generateForPlatform(PlatformAdmin admin) {
        return build(String.valueOf(admin.getId()))
                .claim("platform", true)
                .compact();
    }

    /**
     * Reads whichever kind of token this is, in a single parse.
     *
     * @throws io.jsonwebtoken.JwtException when the token is malformed, tampered with or expired
     */
    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        boolean platform = Boolean.TRUE.equals(claims.get("platform", Boolean.class));
        return new TokenClaims(Long.valueOf(claims.getSubject()), platform);
    }

    public Instant expiresAt() {
        return Instant.now().plus(expiration);
    }

    private JwtBuilder build(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key);
    }

    /** @param subjectId the user id or the platform admin id, depending on {@code platform} */
    public record TokenClaims(Long subjectId, boolean platform) {
    }
}
