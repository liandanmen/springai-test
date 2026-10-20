package org.example.springaitest.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.springaitest.dto.ChatRequest;
import org.example.springaitest.result.Result;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "chat接口", description = "chat接口")
public class ChatController {

    private final ChatClient chatClient;


    @PostMapping(value = "/ai/chat")
    @Operation(summary = "chat接口", description = "chat接口")
    public Result<String> chat(@RequestBody ChatRequest request) {
        String result = chatClient.prompt()
                .user(request.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, request.getChatId()))
                .call()
                .content();
        return Result.success(result);
    }
}
