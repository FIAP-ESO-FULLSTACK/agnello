package com.exemplo.carrinho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@WebMvcTest(CarrinhoController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-must-have-at-least-32-bytes!!",
        "services.estoque.url=http://estoque-service:8080/estoque"
})
class CarrinhoControllerSecurityTest {

    private static final String SECRET = "test-secret-must-have-at-least-32-bytes!!";

    @Autowired private MockMvc mockMvc;
    @MockBean private RestTemplate restTemplate;
    @MockBean private CarrinhoEventPublisher eventPublisher;

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
    void healthDeveSerPublico() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void carrinhoSemTokenDeveRetornar401() throws Exception {
        mockMvc.perform(get("/carrinho"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void carrinhoComTokenInvalidoDeveRetornar401() throws Exception {
        mockMvc.perform(get("/carrinho").header("Authorization", "Bearer falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void carrinhoComTokenValidoDevePropagarAuthorizationHeaderAoEstoque() throws Exception {
        String token = tokenValido();
        when(restTemplate.exchange(
                eq("http://estoque-service:8080/estoque"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("estoque", "mock")));

        mockMvc.perform(get("/carrinho").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servico").value("carrinho-service"))
                .andExpect(jsonPath("$.estoqueRecebido.estoque").value("mock"));

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(1)).exchange(
                eq("http://estoque-service:8080/estoque"),
                eq(HttpMethod.GET),
                captor.capture(),
                any(ParameterizedTypeReference.class));

        assertThat(captor.getValue().getHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer " + token);
    }

    @Test
    void checkoutSemTokenDeveRetornar401() throws Exception {
        mockMvc.perform(post("/carrinho/checkout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkoutComTokenValidoDevePublicarNaFila() throws Exception {
        String token = tokenValido();

        mockMvc.perform(post("/carrinho/checkout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventoPublicado").exists());

        verify(eventPublisher, times(1)).publicar(any(String.class));
    }
}
