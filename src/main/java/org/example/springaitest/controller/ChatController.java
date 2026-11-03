package org.example.springaitest.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springaitest.dto.ChatRequest;
import org.example.springaitest.exception.BaseException;
import org.example.springaitest.handler.JwtAuthenticationInterceptor;
import org.example.springaitest.mapper.AiSessionMapper;
import org.example.springaitest.pojo.AiSession;
import org.example.springaitest.result.Result;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "chat接口", description = "chat接口")
public class ChatController {

    @Qualifier("chatClient")
    private final ChatClient chatClient;
    private final AiSessionMapper aiSessionMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "chat流式接口", description = "SSE流式chat接口")
    public Flux<String> chatStream(@RequestBody ChatRequest request,
                                   @RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        //首先校验会话
        AiSession session = requireOwnedSession(request.getChatId(), userId);
        updateTitleIfNew(session, userId, request.getMessage());   //更新会话标题
        return chatClient.prompt()     // 创建一个提示
                .user(request.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, session.getConversationId()))
                .stream()
                .content()
                // 流中途出错时把错误当成最后一个分片发给前端。若让异常冒泡到全局异常处理器，
                // 响应头已经是 text/event-stream，Result 无法再用 JSON 写回去
                // （No converter for [class Result] with preset Content-Type 'text/event-stream'）
                .onErrorResume(ex -> {
                    log.error("流式对话失败, conversationId={}", session.getConversationId(), ex);
                    String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    return Flux.just("\n\n❌ 生成中断：" + detail);
                });
    }

    // 校验会话
    private AiSession requireOwnedSession(String chatId, Long userId) {
        AiSession session = aiSessionMapper.selectOne(Wrappers.<AiSession>lambdaQuery()
                .eq(AiSession::getId, chatId)
                .eq(AiSession::getUserId, userId));
        if (session == null) {
            throw new BaseException("会话不存在或无权访问");
        }
        return session;
    }

    // 更新会话标题
    private void updateTitleIfNew(AiSession session, Long userId, String message) {
        if (!"新会话".equals(session.getTitle()) || message == null) {
            return;
        }
        String title = message.trim();
        if (title.length() > 32) title = title.substring(0, 32);
        aiSessionMapper.update(null, Wrappers.<AiSession>lambdaUpdate()
                .eq(AiSession::getId, session.getId())
                .eq(AiSession::getUserId, userId)
                .set(AiSession::getTitle, title));
    }

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
        stringRedisTemplate.delete("session:list" + userId);
        // 获取结果
        String result = chatClient.prompt()
                .user(request.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, session.getConversationId()))
                .call()
                .content();
        return Result.success(result);
    }
}
