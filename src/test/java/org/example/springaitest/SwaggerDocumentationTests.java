package org.example.springaitest;

import io.swagger.v3.oas.models.OpenAPI;
import org.example.springaitest.controller.ChatController;
import org.example.springaitest.controller.ChatHistoryController;
import org.example.springaitest.mapper.AiSessionMapper;
import org.example.springaitest.controller.SoupGameController;
import org.example.springaitest.handler.GlobalExceptionHandler;
import org.example.springaitest.service.SoupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(SwaggerDocumentationTests.TestConfiguration.class)
class SwaggerDocumentationTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void generatesOpenApiWithControllerOperations() throws Exception {
        String response = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn().getResponse().getContentAsString();

        JsonNode document = JsonMapper.builder().build().readTree(response);
        assertTrue(document.path("openapi").asText().startsWith("3."));
        assertEquals("chat接口", document.at("/paths/~1api~1ai~1chat/post/summary").asText());
        assertEquals("获取当前用户的聊天会话", document.at("/paths/~1api~1chat_history~1sessions/get/summary").asText());
        assertEquals("新开一局", document.at("/paths/~1api~1soup~1start/post/summary").asText());
    }

    @Test
    void servesSwaggerUiAndConfiguration() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void loadsOnlyOneSwaggerModelAndAnnotationDefinition() throws Exception {
        ClassLoader classLoader = OpenAPI.class.getClassLoader();
        assertEquals(1, Collections.list(classLoader.getResources("io/swagger/v3/oas/models/OpenAPI.class")).size());
        assertEquals(1, Collections.list(classLoader.getResources("io/swagger/v3/oas/annotations/Operation.class")).size());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableConfigurationProperties({SpringDocConfigProperties.class, SwaggerUiConfigProperties.class,
            SwaggerUiOAuthProperties.class})
    @ImportAutoConfiguration({JacksonAutoConfiguration.class, SpringDocConfiguration.class,
            SpringDocSpecPropertiesConfiguration.class, SpringDocWebMvcConfiguration.class, SwaggerConfig.class})
    @Import({ChatController.class, ChatHistoryController.class, SoupGameController.class, GlobalExceptionHandler.class})
    static class TestConfiguration {

        @Bean
        ChatClient chatClient() {
            return mock(ChatClient.class);
        }

        @Bean
        ChatMemoryRepository chatMemoryRepository() {
            return mock(ChatMemoryRepository.class);
        }

        /**
         * ChatController 用字段注入拿了 StringRedisTemplate，这个切片测试不会加载
         * Redis 自动配置，所以必须自己补一个 mock，否则整个切片上下文起不来。
         */
        @Bean
        org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate() {
            return mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        }

        @Bean
        AiSessionMapper aiSessionMapper() {
            return mock(AiSessionMapper.class);
        }

        @Bean
        SoupService soupService() {
            return mock(SoupService.class);
        }

        /** ChatHistoryController 的构造参数，切片里没有实现类，必须补 mock */
        @Bean
        org.example.springaitest.service.ChatHistoryService chatHistoryService() {
            return mock(org.example.springaitest.service.ChatHistoryService.class);
        }
    }
}
