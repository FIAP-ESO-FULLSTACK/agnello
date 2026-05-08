package com.exemplo.auth;

public record AuthResponse(String token, String tipo, long expiraEmSegundos) {
}
