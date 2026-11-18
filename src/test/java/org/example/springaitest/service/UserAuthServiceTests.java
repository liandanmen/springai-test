package org.example.springaitest.service;

import io.jsonwebtoken.Claims;
import org.example.springaitest.dto.UserCredentials;
import org.example.springaitest.exception.BaseException;
import org.example.springaitest.mapper.UserAccountMapper;
import org.example.springaitest.pojo.UserAccount;
import org.example.springaitest.pojo.UserLogin;
import org.example.springaitest.service.Impl.UserAuthServiceImpl;
import org.example.springaitest.service.JwtSessionService;
import org.mockito.Mockito;
import org.example.springaitest.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.HashMap;
import java.util.Map;

class UserAuthServiceTests {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long";

    private UserAuthService userAuthService;
    private UserAccountMapper userAccountMapper;
    private Map<String, UserAccount> accounts;

    @BeforeEach
    void setUp() {
        accounts = new HashMap<>();
        userAccountMapper = Mockito.mock(UserAccountMapper.class);
        Mockito.when(userAccountMapper.insert(Mockito.any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            if (accounts.containsKey(account.getUsername())) throw new DuplicateKeyException("duplicate");
            account.setId(1L);
            accounts.put(account.getUsername(), account);
            return 1;
        });
        Mockito.when(userAccountMapper.selectOne(Mockito.any())).thenAnswer(invocation ->
                accounts.values().stream().findFirst().orElse(null));
        JwtSessionService sessionService = Mockito.mock(JwtSessionService.class);
        Mockito.when(sessionService.issue(Mockito.anyLong(), Mockito.anyString()))
                .thenAnswer(invocation -> JwtUtil.createJWT(SECRET, 60_000,
                        java.util.Map.of("userId", invocation.getArgument(0), "username", invocation.getArgument(1))));
        userAuthService = new UserAuthServiceImpl(userAccountMapper, new BCryptPasswordEncoder(4), sessionService);
    }

    @Test
    void registersWithHashedPasswordAndReturnsJwt() {
        UserLogin login = userAuthService.register(new UserCredentials("alice", "correct-horse"));
        String passwordHash = accounts.get("alice").getPasswordHash();
        Claims claims = JwtUtil.parseJWT(SECRET, login.getToken());

        assertEquals("alice", login.getUsername());
        assertNotEquals("correct-horse", passwordHash);
        assertEquals(login.getId().intValue(), claims.get("userId", Integer.class));
        assertEquals("alice", claims.get("username", String.class));
    }

    @Test
    void loginIssuesTokenOnlyForMatchingPassword() {
        userAuthService.register(new UserCredentials("alice", "correct-horse"));

        UserLogin login = userAuthService.login(new UserCredentials("alice", "correct-horse"));

        assertEquals("alice", login.getUsername());
        assertEquals("alice", JwtUtil.parseJWT(SECRET, login.getToken()).get("username", String.class));
        assertThrows(BaseException.class,
                () -> userAuthService.login(new UserCredentials("alice", "wrong-password")));
    }

    @Test
    void rejectsDuplicateUsernamesAndWeakRegistrationPasswords() {
        userAuthService.register(new UserCredentials("alice", "correct-horse"));

        assertThrows(BaseException.class,
                () -> userAuthService.register(new UserCredentials("alice", "another-password")));
        assertThrows(BaseException.class,
                () -> userAuthService.register(new UserCredentials("bob", "short")));
    }
}
