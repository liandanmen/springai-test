package org.example.springaitest.service;

import org.example.springaitest.dto.ChatSessionResponse;
import org.example.springaitest.dto.CreateChatSessionRequest;
import org.example.springaitest.handler.JwtAuthenticationInterceptor;
import org.example.springaitest.result.Result;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

public interface ChatHistoryService {
    ChatSessionResponse createSession(CreateChatSessionRequest request,Long userId);

    List<ChatSessionResponse> sessions(Long userId);

    ChatSessionResponse detail(String sessionId, Long userId);

    void delete(String sessionId, Long userId);
}
