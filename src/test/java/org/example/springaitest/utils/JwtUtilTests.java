package org.example.springaitest.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTests {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long";

    @Test
    void createsAndParsesSignedToken() {
        String token = JwtUtil.createJWT(SECRET, 60_000, Map.of("userId", 12L, "username", "alice"));

        Claims claims = JwtUtil.parseJWT(SECRET, token);

        assertEquals(12, claims.get("userId", Integer.class));
        assertEquals("alice", claims.get("username", String.class));
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        String token = JwtUtil.createJWT(SECRET, 60_000, Map.of("userId", 12L, "username", "alice"));

        assertThrows(JwtException.class, () -> JwtUtil.parseJWT(
                "another-test-secret-that-is-at-least-32-bytes-long", token));
    }

    @Test
    void rejectsSecretsShorterThanHs256Requirement() {
        assertThrows(IllegalArgumentException.class,
                () -> JwtUtil.createJWT("too-short", 60_000, Map.of("userId", 12L)));
    }
}
