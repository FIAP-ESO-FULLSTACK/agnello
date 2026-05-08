package com.exemplo.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

    private static final String SECRET = "test-secret-must-have-at-least-32-bytes!!";
    private static final long EXPIRATION = 60_000L;

    private final JwtService jwtService = new JwtService(SECRET, EXPIRATION);

    @Test
    void deveGerarTokenComSubjectInformado() {
        String token = jwtService.generateToken("admin");

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("admin");
    }

    @Test
    void deveGerarTokenComExpiracaoFutura() {
        String token = jwtService.generateToken("admin");

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void tokensGeradosDevemTerFormatoJWT() {
        String token = jwtService.generateToken("admin");

        assertThat(token.split("\\.")).hasSize(3);
    }
}
