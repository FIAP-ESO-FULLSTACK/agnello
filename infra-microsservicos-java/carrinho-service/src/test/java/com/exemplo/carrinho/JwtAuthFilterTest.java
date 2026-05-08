package com.exemplo.carrinho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void semHeaderAuthorizationDevePassarRequisicaoAdiante() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        new JwtAuthFilter(jwtService).doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenInvalidoDeveRetornar401() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer xyz");
        when(jwtService.isTokenValid("xyz")).thenReturn(false);

        new JwtAuthFilter(jwtService).doFilter(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), any());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void tokenValidoDeveAutenticar() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer abc.def.ghi");
        when(jwtService.isTokenValid("abc.def.ghi")).thenReturn(true);
        when(jwtService.extractUsername("abc.def.ghi")).thenReturn("admin");

        new JwtAuthFilter(jwtService).doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin");
    }
}
