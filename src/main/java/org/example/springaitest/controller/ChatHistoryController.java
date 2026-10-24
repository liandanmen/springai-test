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
import org.example.springaitest.service.ChatHistoryService;
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

    private final ChatHistoryService chatHistoryService;

    @PostMapping("/sessions")
    @Operation(summary = "创建聊天会话")
    public Result<ChatSessionResponse> createSession(
            @RequestBody(required = false) CreateChatSessionRequest request,
            @RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        ChatSessionResponse session = chatHistoryService.createSession(request, userId);
        return Result.success(session);
    }

    @GetMapping("/sessions")
    @Operation(summary = "获取当前用户的聊天会话")
    public Result<List<ChatSessionResponse>> sessions(
            @RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        List<ChatSessionResponse> sessions = chatHistoryService.sessions(userId);
        return Result.success(sessions);
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "获取当前用户的聊天记录")
    public Result<ChatSessionResponse> detail(
            @PathVariable String sessionId,
            @RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        ChatSessionResponse session = chatHistoryService.detail(sessionId, userId);
        return Result.success(session);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "删除当前用户的聊天会话")
    public Result<Void> delete(
            @PathVariable String sessionId,
            @RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        chatHistoryService.delete(sessionId, userId);
        return Result.success();
    }


}
