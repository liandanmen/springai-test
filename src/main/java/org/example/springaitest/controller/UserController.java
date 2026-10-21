package org.example.springaitest.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.example.springaitest.dto.UserCredentials;
import org.example.springaitest.pojo.UserLogin;
import org.example.springaitest.result.Result;
import org.example.springaitest.service.UserAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "user", description = "用户管理接口")
public class UserController {
    private final UserAuthService userAuthService;

    public UserController(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<UserLogin> register(@RequestBody UserCredentials credentials) {
        return Result.success(userAuthService.register(credentials));
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<UserLogin> login(@RequestBody UserCredentials credentials) {
        return Result.success(userAuthService.login(credentials));
    }
}
