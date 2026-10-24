package org.example.springaitest.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.springaitest.dto.UserCredentials;
import org.example.springaitest.exception.BaseException;
import org.example.springaitest.mapper.UserAccountMapper;
import org.example.springaitest.pojo.UserAccount;
import org.example.springaitest.pojo.UserLogin;
import org.example.springaitest.service.UserAuthService;
import org.example.springaitest.service.JwtSessionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
@Service
public class UserAuthServiceImpl implements UserAuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtSessionService jwtSessionService;

    public UserAuthServiceImpl(UserAccountMapper userAccountMapper,
                               PasswordEncoder passwordEncoder,
                               JwtSessionService jwtSessionService) {
        this.userAccountMapper = userAccountMapper;
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

        UserAccount newAccount = new UserAccount();
        newAccount.setUsername(username);
        newAccount.setPasswordHash(passwordHash);
        try {
            userAccountMapper.insert(newAccount);
        } catch (DuplicateKeyException exception) {
            throw new BaseException("用户名已被注册");
        }

        return createLoginResult(newAccount.getId(), username);
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
        UserAccount account = userAccountMapper.selectOne(
                Wrappers.<UserAccount>lambdaQuery().eq(UserAccount::getUsername, username));

        if (account == null || !passwordEncoder.matches(credentials.password(), account.getPasswordHash())) {
            throw new BaseException("用户名或密码错误");
        }

        return createLoginResult(account.getId(), account.getUsername());
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

}
