package com.exemplo.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final long expirationMs;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.expirationMs = expirationMs;
    }

    @GetMapping({"", "/"})
    public Map<String, Object> raiz() {
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("servico", "auth-service");
        resposta.put("mensagem", "Servico de autenticacao - emite JWT para acesso aos servicos protegidos");
        resposta.put("endpoints", List.of("POST /auth/login", "/health"));

        return resposta;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    request.username(),
                    request.password()));
            String token = jwtService.generateToken(request.username());
            return ResponseEntity.ok(new AuthResponse(token, "Bearer", expirationMs / 1000));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "servico", "auth-service",
                "status", "UP"
        );
    }
}
