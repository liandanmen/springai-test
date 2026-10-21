package org.example.springaitest.service.Impl;

import org.example.springaitest.dto.UserCredentials;
import org.example.springaitest.exception.BaseException;
import org.example.springaitest.pojo.UserLogin;
import org.example.springaitest.service.UserAuthService;
import org.example.springaitest.service.JwtSessionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
@Service
public class UserAuthServiceImpl implements UserAuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtSessionService jwtSessionService;

    public UserAuthServiceImpl(JdbcTemplate jdbcTemplate,
                               PasswordEncoder passwordEncoder,
                               JwtSessionService jwtSessionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtSessionService = jwtSessionService;
    }

    @Override
    public UserLogin register(UserCredentials credentials) {
        //注册
        if (credentials == null) {
            throw new BaseException("用户名和密码不能为空");
        }
        String username = validateUsername(credentials.username());
        String password = validatePassword(credentials.password());
        String passwordHash = passwordEncoder.encode(password);

        try {
            jdbcTemplate.update("INSERT INTO app_user (username, password_hash) VALUES (?, ?)",
                    username, passwordHash);
        } catch (DuplicateKeyException exception) {
            throw new BaseException("用户名已被注册");
        }

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = ?", Long.class, username);
        return createLoginResult(userId, username);
    }

    @Override
    public UserLogin login(UserCredentials credentials) {
        if (credentials == null || credentials.username() == null || credentials.password() == null
                || credentials.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BaseException("用户名或密码错误");
        }

        String username = credentials.username().trim();
        if (username.isEmpty() || username.length() > 64) {
            throw new BaseException("用户名或密码错误");
        }
        List<UserAccount> accounts = jdbcTemplate.query(
                "SELECT id, username, password_hash FROM app_user WHERE username = ?",
                (resultSet, rowNumber) -> new UserAccount(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash")),
                username);

        if (accounts.isEmpty() || !passwordEncoder.matches(credentials.password(), accounts.getFirst().passwordHash())) {
            throw new BaseException("用户名或密码错误");
        }

        UserAccount account = accounts.getFirst();
        return createLoginResult(account.id(), account.username());
    }

    private UserLogin createLoginResult(Long userId, String username) {
        String token = jwtSessionService.issue(userId, username);

        return UserLogin.builder()
                .id(userId)
                .username(username)
                .token(token)
                .build();
    }

    // 验证用户名
    private String validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BaseException("用户名不能为空");
        }
        String normalizedUsername = username.trim();
        if (normalizedUsername.length() < 3 || normalizedUsername.length() > 64) {
            throw new BaseException("用户名长度必须为 3 到 64 个字符");
        }
        return normalizedUsername;
    }

    // 验证密码
    private String validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8) {
            throw new BaseException("密码至少需要 8 个字符");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BaseException("密码 UTF-8 长度不能超过 72 字节");
        }
        return password;
    }

    // 用户账号信息
    private record UserAccount(long id, String username, String passwordHash) {
    }
}
