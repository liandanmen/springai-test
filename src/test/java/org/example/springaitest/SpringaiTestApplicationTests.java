package org.example.springaitest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

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
