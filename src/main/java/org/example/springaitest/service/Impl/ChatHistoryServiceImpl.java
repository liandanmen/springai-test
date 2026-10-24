package org.example.springaitest.service.Impl;

import java.time.Duration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.example.springaitest.dto.ChatMessageResponse;
import org.example.springaitest.dto.ChatSessionResponse;
import org.example.springaitest.dto.CreateChatSessionRequest;
import org.example.springaitest.exception.BaseException;
import org.example.springaitest.mapper.AiSessionMapper;
import org.example.springaitest.pojo.AiSession;
import org.example.springaitest.service.ChatHistoryService;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class ChatHistoryServiceImpl implements ChatHistoryService {

    @Autowired
    private ChatMemoryRepository chatMemoryRepository;
    @Autowired
    private AiSessionMapper aiSessionMapper;
    //将会话列表加入缓存
    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Duration SESSION_LIST_TTL = Duration.ofMinutes(5);

    private final JsonMapper jsonMapper;


    private final String SESSION_LIST_KEY = "session:list";

    @Override
    public ChatSessionResponse createSession(CreateChatSessionRequest request, Long userId) {
        AiSession session = new AiSession();
        session.setId(UUID.randomUUID().toString());
        session.setConversationId(UUID.randomUUID().toString());
        session.setUserId(userId);
        String title = request == null ? null : request.title();
        title = title == null || title.isBlank() ? "新会话" : title.trim();
        session.setTitle(title.length() > 128 ? title.substring(0, 128) : title);
        aiSessionMapper.insert(session);
        return new ChatSessionResponse(session.getId(), session.getTitle(), List.of());
    }

    @Override
    public List<ChatSessionResponse> sessions(Long userId) {
        String key = SESSION_LIST_KEY + userId;
        String cacheSessions = redisTemplate.opsForValue().get(key);
        //反序列化json为list
        if (cacheSessions != null) {
            try {
                return jsonMapper.readValue(
                        cacheSessions,
                        new TypeReference<List<ChatSessionResponse>>() {}
                );
            } catch (JacksonException e) {
                redisTemplate.delete(key); // 缓存无法解析，删除后从数据库重建
            }
        }
        List<ChatSessionResponse> sessions = aiSessionMapper.selectList(Wrappers.<AiSession>lambdaQuery()
                        .eq(AiSession::getUserId, userId)
                        .orderByDesc(AiSession::getUpdatedAt))
                .stream()
                .map(session -> new ChatSessionResponse(session.getId(), session.getTitle(), List.of()))
                .toList();
        try {
            redisTemplate.opsForValue().set(
                    key,
                    jsonMapper.writeValueAsString(sessions),
                    SESSION_LIST_TTL
            );
        } catch (JacksonException e) {
            throw new IllegalStateException("会话列表缓存序列化失败", e);
        }
        return sessions;
    }

    @Override
    public ChatSessionResponse detail(String sessionId, Long userId) {
        AiSession session = requireOwnedSession(sessionId, userId);
        List<ChatMessageResponse> messages = chatMemoryRepository
                .findByConversationId(session.getConversationId()).stream()
                .map(message -> new ChatMessageResponse(
                        message.getMessageType().getValue(), message.getText()))
                .toList();
        return new ChatSessionResponse(session.getId(), session.getTitle(), messages);
    }

    @Override
    public void delete(String sessionId, Long userId) {
        AiSession session = requireOwnedSession(sessionId, userId);
        redisTemplate.delete(SESSION_LIST_KEY + userId);
        aiSessionMapper.delete(Wrappers.<AiSession>lambdaQuery()
                .eq(AiSession::getId, sessionId)
                .eq(AiSession::getUserId, userId));
        chatMemoryRepository.deleteByConversationId(session.getConversationId());

    }

    private AiSession requireOwnedSession(String sessionId, Long userId) {
        AiSession session = aiSessionMapper.selectOne(Wrappers.<AiSession>lambdaQuery()
                .eq(AiSession::getId, sessionId)
                .eq(AiSession::getUserId, userId));
        if (session == null) throw new BaseException("会话不存在或无权访问");
        return session;
    }
}
