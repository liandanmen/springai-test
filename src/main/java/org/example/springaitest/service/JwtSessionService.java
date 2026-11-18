package org.example.springaitest.service;

public interface JwtSessionService {

    //将token和userId绑定，存入redis
    String issue(Long userId, String username);


    //原子性替换当前token，使得上一个token无法被 replay
    /** Atomically replace the current token so the previous token cannot be replayed. */
    String rotate(Long userId, String username, String currentToken);



}