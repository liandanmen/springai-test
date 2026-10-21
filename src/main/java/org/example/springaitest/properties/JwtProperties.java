package org.example.springaitest.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

@Component
@ConfigurationProperties(prefix = "springai.jwt")
@Data
public class JwtProperties {
    /**
     * 用户生成jwt令牌相关配置
     */
    private String userSecretKey;
    private long userTtl;
    private String userTokenName;


    @PostConstruct
    public void validate() {
        if (userSecretKey == null || userSecretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("springai.jwt.user-secret-key 必须至少为 32 个 UTF-8 字节（请设置 JWT_USER_SECRET_KEY 环境变量）");
        }
        if (userTtl <= 0) {
            throw new IllegalStateException("springai.jwt.user-ttl 必须大于 0");
        }
        if (userTokenName == null || userTokenName.isBlank()) {
            throw new IllegalStateException("springai.jwt.user-token-name 不能为空");
        }
    }
}
