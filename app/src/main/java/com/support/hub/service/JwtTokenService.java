package com.support.hub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtBuilder;

@Service
public class JwtTokenService {

    @Value("${jwt.secret:your-256-bit-secret-key-for-testing-purposes-only}")
    private String jwtSecret;

    private static final long EXPIRATION_TIME = 3600000;

    public String generateToken(String externalId, String role) {
        Instant now = Instant.now();
        Instant expiration = now.plusMillis(EXPIRATION_TIME);

        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        return Jwts.builder()
                .subject(externalId)
                .claim("roles", List.of(role))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    public Long getExpirationTime() {
        return EXPIRATION_TIME ;
    }
}

