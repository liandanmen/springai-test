package org.example.springaitest.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.example.springaitest.handler.JwtAuthenticationInterceptor;
import org.example.springaitest.handler.AiChatRateLimitInterceptor;



@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;
    private final AiChatRateLimitInterceptor aiChatRateLimitInterceptor;

    public WebConfiguration(JwtAuthenticationInterceptor jwtAuthenticationInterceptor,
                            AiChatRateLimitInterceptor aiChatRateLimitInterceptor) {
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
        this.aiChatRateLimitInterceptor = aiChatRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthenticationInterceptor).addPathPatterns("/api/**").order(0);
        registry.addInterceptor(aiChatRateLimitInterceptor).addPathPatterns("/api/ai/chat").order(1);
        registry.addInterceptor(aiChatRateLimitInterceptor).addPathPatterns("/api/soup/guess").order(2);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Retry-After")
                .allowCredentials(true);
    }



}
