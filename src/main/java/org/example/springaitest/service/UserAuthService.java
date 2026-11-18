package org.example.springaitest.service;

import org.example.springaitest.dto.UserCredentials;
import org.example.springaitest.pojo.UserLogin;

public interface UserAuthService {
    UserLogin register(UserCredentials credentials);

    UserLogin login(UserCredentials credentials);
}