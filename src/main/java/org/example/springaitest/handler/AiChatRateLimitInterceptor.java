package org.example.springaitest.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.springaitest.service.AiChatRateLimiter;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class AiChatRateLimitInterceptor implements HandlerInterceptor {

    private final AiChatRateLimiter rateLimiter;

    public AiChatRateLimitInterceptor(AiChatRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Object userId = request.getAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE);
        if (!(userId instanceof Long authenticatedUserId)) {
            return reject(response, 401, "请先登录或登录已过期");
        }
        try {
            long retryAfter = rateLimiter.acquire(authenticatedUserId);
            if (retryAfter == 0) {
                return true;
            }
            response.setHeader("Retry-After", Long.toString(retryAfter));
            return reject(response, 429, "请求过于频繁，同一用户每分钟最多进行20次AI会话请求，请稍后重试");
        } catch (DataAccessException exception) {
            // Fail closed: a Redis outage must not silently disable the quota.
            return reject(response, 503, "限流服务暂不可用，请稍后重试");
        }
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":0,\"msg\":\"" + message + "\",\"data\":null}");
        return false;
    }
}
