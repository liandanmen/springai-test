package org.example.springaitest.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.springaitest.dto.ChatMessageResponse;
import org.example.springaitest.dto.ChatSessionResponse;
import org.example.springaitest.dto.CreateChatSessionRequest;
import org.example.springaitest.exception.BaseException;
import org.example.springaitest.handler.JwtAuthenticationInterceptor;
import org.example.springaitest.mapper.AiSessionMapper;
import org.example.springaitest.pojo.AiSession;
import org.example.springaitest.result.Result;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat_history")
@RequiredArgsConstructor
@Tag(name = "chat历史接口")
public class ChatHistoryController {
    private final ChatMemoryRepository chatMemoryRepository;
    private final AiSessionMapper aiSessionMapper;

    @PostMapping("/sessions")
    @Operation(summary = "创建聊天会话")
    public Result<ChatSessionResponse> createSession(
            @RequestBody(required = false) CreateChatSessionRequest request,
            @RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        AiSession session = new AiSession();
        session.setId(UUID.randomUUID().toString());
        session.setConversationId(UUID.randomUUID().toString());
        session.setUserId(userId);
        String title = request == null ? null : request.title();
        title = title == null || title.isBlank() ? "新会话" : title.trim();
        session.setTitle(title.length() > 128 ? title.substring(0, 128) : title);
        aiSessionMapper.insert(session);
        return Result.success(new ChatSessionResponse(session.getId(), session.getTitle(), List.of()));
    }

    @GetMapping("/sessions")
    @Operation(summary = "获取当前用户的聊天会话")
    public Result<List<ChatSessionResponse>> sessions(
            @RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        List<ChatSessionResponse> sessions = aiSessionMapper.selectList(Wrappers.<AiSession>lambdaQuery()
                        .eq(AiSession::getUserId, userId)
                        .orderByDesc(AiSession::getUpdatedAt))
                .stream()
                .map(session -> new ChatSessionResponse(session.getId(), session.getTitle(), List.of()))
                .toList();
        return Result.success(sessions);
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "获取当前用户的聊天记录")
    public Result<ChatSessionResponse> detail(
            @PathVariable String sessionId,
            @RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        AiSession session = requireOwnedSession(sessionId, userId);
        List<ChatMessageResponse> messages = chatMemoryRepository
                .findByConversationId(session.getConversationId()).stream()
                .map(message -> new ChatMessageResponse(
                        message.getMessageType().getValue(), message.getText()))
                .toList();
        return Result.success(new ChatSessionResponse(session.getId(), session.getTitle(), messages));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "删除当前用户的聊天会话")
    public Result<Void> delete(
            @PathVariable String sessionId,
            @RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        AiSession session = requireOwnedSession(sessionId, userId);
        aiSessionMapper.delete(Wrappers.<AiSession>lambdaQuery()
                .eq(AiSession::getId, sessionId)
                .eq(AiSession::getUserId, userId));
        chatMemoryRepository.deleteByConversationId(session.getConversationId());
        return Result.success();
    }

    private AiSession requireOwnedSession(String sessionId, Long userId) {
        AiSession session = aiSessionMapper.selectOne(Wrappers.<AiSession>lambdaQuery()
                .eq(AiSession::getId, sessionId)
                .eq(AiSession::getUserId, userId));
        if (session == null) throw new BaseException("会话不存在或无权访问");
        return session;
    }
}
