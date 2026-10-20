package org.example.springaitest.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.springaitest.result.Result;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat_history")
@RequiredArgsConstructor
@Tag(name = "chat历史接口")

public class ChatHistoryController {
    private final ChatMemoryRepository  chatMemoryRepository;

    @GetMapping("/sessions")
    @Operation(summary = "获取会话列表", description = "获取会话列表")
    public Result<List<String>> sessions(){

        return Result.success(chatMemoryRepository.findConversationIds());
    }
    @GetMapping("/{session}")
    @Operation(summary = "获取会话详情", description = "获取会话详情")
    public Result<List<Map<String,String>>> getChatIds(@PathVariable("session") String session) {
        return Result.success(chatMemoryRepository.findByConversationId(session).stream().map(message -> Map.of(
                "role", message.getMessageType().getValue(),
                "content", message.getText()
        )).toList());
    }

}
