package org.example.springaitest.handler;

import org.example.springaitest.properties.JwtProperties;
import org.example.springaitest.utils.JwtUtil;
import org.example.springaitest.service.JwtSessionService;
import org.mockito.Mockito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationInterceptorTests {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long";

    private JwtAuthenticationInterceptor interceptor;
    private JwtSessionService sessionService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setUserSecretKey(SECRET);
        properties.setUserTtl(60_000);
        properties.setUserTokenName("Authorization");
        sessionService = Mockito.mock(JwtSessionService.class);
        interceptor = new JwtAuthenticationInterceptor(properties, sessionService);
    }

    @Test
    void acceptsBearerTokenAndSetsAuthenticatedUserAttributes() throws Exception {
        String token = JwtUtil.createJWT(SECRET, 60_000, Map.of("userId", 42L, "username", "alice"));
        Mockito.when(sessionService.rotate(42L, "alice", token)).thenReturn("rotated-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat_history/sessions");
        request.addHeader("Authorization", "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        assertEquals("Bearer rotated-token", response.getHeader("Authorization"));
        assertEquals(42L, request.getAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE));
        assertEquals("alice", request.getAttribute(JwtAuthenticationInterceptor.USERNAME_ATTRIBUTE));
    }

    @Test
    void rejectsMissingAndInvalidTokensWithUnauthorizedStatus() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chat_history/sessions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());

        request.addHeader("Authorization", "Bearer invalid-token");
        response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void permitsCorsPreflightWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/chat_history/sessions");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
}
