package com.exemplo.estoque;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

    private static final String SECRET = "test-secret-must-have-at-least-32-bytes!!";
    private final JwtService jwtService = new JwtService(SECRET);

    private String tokenAssinadoCom(String secret, String subject, long ttlMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void deveValidarTokenAssinadoComMesmoSegredo() {
        String token = tokenAssinadoCom(SECRET, "admin", 60_000);
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
    }

    @Test
    void deveRejeitarTokenAssinadoComOutroSegredo() {
        String token = tokenAssinadoCom("outro-secret-must-have-at-least-32-bytes!!", "admin", 60_000);
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void deveRejeitarTokenExpirado() {
        String token = tokenAssinadoCom(SECRET, "admin", -1_000);
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void deveRejeitarTokenMalFormado() {
        assertThat(jwtService.isTokenValid("isso-nao-eh-um-jwt")).isFalse();
        assertThat(jwtService.isTokenValid("")).isFalse();
    }
}
