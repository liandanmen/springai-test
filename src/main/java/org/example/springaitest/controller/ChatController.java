package org.example.springaitest.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.springaitest.dto.ChatRequest;
import org.example.springaitest.exception.BaseException;
import org.example.springaitest.handler.JwtAuthenticationInterceptor;
import org.example.springaitest.mapper.AiSessionMapper;
import org.example.springaitest.pojo.AiSession;
import org.example.springaitest.result.Result;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "chat接口", description = "chat接口")
public class ChatController {

    @Qualifier("chatClient")
    private final ChatClient chatClient;
    private final AiSessionMapper aiSessionMapper;


    @PostMapping(value = "/ai/chat")
    @Operation(summary = "chat接口", description = "chat接口")
    public Result<String> chat(@RequestBody ChatRequest request,
            @RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        // 校验会话
        AiSession session = aiSessionMapper.selectOne(Wrappers.<AiSession>lambdaQuery()
                .eq(AiSession::getId, request.getChatId())
                .eq(AiSession::getUserId, userId));
        if (session == null) {
            throw new BaseException("会话不存在或无权访问");
        }
        // 更新会话标题
        if ("新会话".equals(session.getTitle()) && request.getMessage() != null) {
            String title = request.getMessage().trim();
            if (title.length() > 32) title = title.substring(0, 32);
            aiSessionMapper.update(null, Wrappers.<AiSession>lambdaUpdate()
                    .eq(AiSession::getId, session.getId())
                    .eq(AiSession::getUserId, userId)
                    .set(AiSession::getTitle, title));
        }
        // 获取结果
        String result = chatClient.prompt()
                .user(request.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, session.getConversationId()))
                .call()
                .content();
        return Result.success(result);
    }
}
