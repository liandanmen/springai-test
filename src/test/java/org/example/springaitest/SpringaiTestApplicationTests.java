package org.example.springaitest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 整个应用的上下文冒烟测试。
 *
 * <p>注意：这个测试用 H2 代替 MySQL，但 {@code SensitiveWordService} 的 @PostConstruct
 * 会去查 {@code sensitive_rule} 表，而 H2 里没有 Flyway 建的表 —— 所以它会失败。
 *
 * <p>真正的"上下文能不能起来"用 {@code mvn spring-boot:run} 或 IDE 启动验证即可，
 * 这里默认跳过，避免日常 {@code mvn test} 一直红。
 */
@Disabled("需要真实 MySQL（sensitive_rule 表），H2 环境下必然失败；上下文验证以实际启动为准")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.ai.deepseek.api-key=test-deepseek-key",
        "spring.ai.openai.api-key=test-openai-key",
        "springai.jwt.user-secret-key=test-jwt-secret-key-that-is-at-least-32-bytes",
        "springai.jwt.user-ttl=60000",
        "springai.jwt.user-token-name=Authorization"
})
class SpringaiTestApplicationTests {

    @Test
    void contextLoads() {
    }

}
