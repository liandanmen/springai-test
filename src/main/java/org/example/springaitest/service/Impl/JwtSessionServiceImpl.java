package org.example.springaitest.service.Impl;

import org.example.springaitest.properties.JwtProperties;
import org.example.springaitest.service.JwtSessionService;
import org.example.springaitest.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@Service
public class JwtSessionServiceImpl implements JwtSessionService {
    private static final String KEY_PREFIX = "auth:jwt:user:";
    //增加对话限流
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT;
    static {
        ROTATE_SCRIPT=new DefaultRedisScript<>();
        ROTATE_SCRIPT.setLocation(new ClassPathResource("RedisUserLogin.lua"));
        ROTATE_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private JwtProperties jwtProperties;

    //颁发token，存入redis
    @Override
    public String issue(Long userId, String username) {
        String token = createToken(userId, username);
        redisTemplate.opsForValue().set(key(userId), token, jwtProperties.getUserTtl(), java.util.concurrent.TimeUnit.MILLISECONDS);
        return token;
    }

    //用户有操作，刷新token（续期）
    @Override
    public String rotate(Long userId, String username, String currentToken) {
        String nextToken = createToken(userId, username);
        Long rotated = redisTemplate.execute(ROTATE_SCRIPT, List.of(key(userId)), currentToken, nextToken,
                Long.toString(jwtProperties.getUserTtl()));
        return Long.valueOf(1).equals(rotated) ? nextToken : null;
    }

    //生成token
    private String createToken(Long userId, String username) {
        return JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(),
                Map.of("userId", userId, "username", username));
    }

    //生成redis key
    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
