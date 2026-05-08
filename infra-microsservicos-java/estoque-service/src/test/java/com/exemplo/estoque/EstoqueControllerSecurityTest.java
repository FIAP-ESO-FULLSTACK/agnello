package com.exemplo.estoque;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@WebMvcTest(EstoqueController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
@TestPropertySource(properties = "jwt.secret=test-secret-must-have-at-least-32-bytes!!")
class EstoqueControllerSecurityTest {

    private static final String SECRET = "test-secret-must-have-at-least-32-bytes!!";

    @Autowired
    private MockMvc mockMvc;

    private String tokenValido() {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject("admin")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void rootDeveSerPublico() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servico").value("estoque-service"));
    }

    @Test
    void healthDeveSerPublico() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void estoqueSemTokenDeveRetornar401() throws Exception {
        mockMvc.perform(get("/estoque"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void estoqueComTokenValidoDeveRetornar200() throws Exception {
        mockMvc.perform(get("/estoque").header("Authorization", "Bearer " + tokenValido()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servico").value("estoque-service"));
    }

    @Test
    void estoqueComTokenInvalidoDeveRetornar401() throws Exception {
        mockMvc.perform(get("/estoque").header("Authorization", "Bearer token-falsificado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void estoqueComTokenAssinadoComOutroSegredoDeveRetornar401() throws Exception {
        long now = System.currentTimeMillis();
        String tokenIntruso = Jwts.builder()
                .subject("admin")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 60_000))
                .signWith(Keys.hmacShaKeyFor("OUTRO-secret-must-have-at-least-32-bytes!!".getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/estoque").header("Authorization", "Bearer " + tokenIntruso))
                .andExpect(status().isUnauthorized());
    }
}
