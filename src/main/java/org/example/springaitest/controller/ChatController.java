package org.example.springaitest.controller;

import lombok.RequiredArgsConstructor;
import org.example.springaitest.dto.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatClient chatClient;

    @PostMapping(value = "/ai/chat", produces = "text/plain;charset=utf-8")
    public String chat(@RequestBody ChatRequest request) {
        return chatClient.prompt()
                .user(request.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, request.getChatId()))
                .call()
                .content();
    }
}
