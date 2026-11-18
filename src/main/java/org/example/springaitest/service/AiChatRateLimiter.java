package org.example.springaitest.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 使用有序集合实现的限流器
 * . */
@Service
public class AiChatRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_REQUESTS = 20;
    private static final String RATE_LIMIT_KEY = "rate-limit:ai-chat:user:%s";
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT;
    static {
        ACQUIRE_SCRIPT = new DefaultRedisScript<>();
        ACQUIRE_SCRIPT.setLocation(new ClassPathResource("RateChat.lua"));
        ACQUIRE_SCRIPT.setResultType(Long.class);
    }
    private final StringRedisTemplate redisTemplate;

    public AiChatRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取令牌，如果令牌不足则返回剩余等待时间（秒）。
     * @param userId 用户ID
     * @return 剩余等待时间（秒），如果为0则表示令牌充足
     */
    public long acquire(Long userId) {
        Long waitMillis = redisTemplate.execute(ACQUIRE_SCRIPT,
                List.of(String.format(RATE_LIMIT_KEY, userId)),
                Long.toString(WINDOW_MILLIS), Integer.toString(MAX_REQUESTS), UUID.randomUUID().toString());
        if (waitMillis == null) {
            throw new DataAccessResourceFailureException("对话频率限制器返回了空结果");
        }
        return (waitMillis + 999) / 1000;
    }
}
