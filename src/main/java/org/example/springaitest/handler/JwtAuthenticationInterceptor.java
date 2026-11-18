package org.example.springaitest.handler;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.springaitest.properties.JwtProperties;
import org.example.springaitest.service.JwtSessionService;
import org.example.springaitest.utils.JwtUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTRIBUTE = "authenticatedUserId";
    public static final String USERNAME_ATTRIBUTE = "authenticatedUsername";

    private static final String UNAUTHORIZED_RESPONSE =
            "{\"code\":0,\"msg\":\"请先登录或登录已过期\",\"data\":null}";

    private final JwtProperties jwtProperties;
    private final JwtSessionService jwtSessionService;

    public JwtAuthenticationInterceptor(JwtProperties jwtProperties, JwtSessionService jwtSessionService) {
        this.jwtProperties = jwtProperties;
        this.jwtSessionService = jwtSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        /* 异步请求(SSE/Flux)处理完，容器会用它再做一次 ASYNC 派发，DispatcherServlet 会把拦截器
         再执行一遍。此时 token 在上一次 preHandle 里已经轮换过，再轮换必然失败，而且响应体
         (SSE 流)已提交，reject() 写 401 会抛 getOutputStream() has already been called。*/
        if (DispatcherType.ASYNC == request.getDispatcherType()) {
            return true;     //直接放行，不再反复轮换 token
        }

        String authorization = request.getHeader(jwtProperties.getUserTokenName());
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return reject(response);
        }

        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            return reject(response);
        }

        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Object userIdClaim = claims.get("userId");
            String username = claims.get("username", String.class);
            if (userIdClaim == null || username == null) {
                return reject(response);
            }
            Long userId = Long.valueOf(userIdClaim.toString());
            String nextToken = jwtSessionService.rotate(userId, username, token);
            if (nextToken == null) {
                return reject(response);
            }
            request.setAttribute(USER_ID_ATTRIBUTE, userId);
            request.setAttribute(USERNAME_ATTRIBUTE, username);
            response.setHeader(jwtProperties.getUserTokenName(), "Bearer " + nextToken);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return reject(response);
        }
    }

    private boolean reject(HttpServletResponse response) throws IOException {
        // 响应已经提交（例如流式响应已经写出）就不要再写，否则会二次提交并抛出异常
        if (response.isCommitted()) {
            return false;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(UNAUTHORIZED_RESPONSE);
        return false;
    }
}
