package org.example.springaitest.service.Impl;

import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.springaitest.constant.SoupPrompt;
import org.example.springaitest.exception.BaseException;
import org.example.springaitest.mapper.SoupGameMapper;
import org.example.springaitest.mapper.SoupGameMessageMapper;
import org.example.springaitest.pojo.SoupGame;
import org.example.springaitest.pojo.SoupGameMessage;
import org.example.springaitest.service.SoupService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SoupServiceImpl implements SoupService {

    private final SoupGameMapper soupGameMapper;
    private final SoupGameMessageMapper soupGameMessageMapper;
    @Qualifier("soupChatClient")
    private final ChatClient soupChatClient;
    private final ChatMemoryRepository chatMemoryRepository;

    private static final Random RANDOM = new Random();
    private static final String ENDED_TIP = "本局已结束，请新开一局。";

    @Override
    public Map<String, String> start(Long userId, String gameId) {
        String[] soup = SoupPrompt.SOUP_PRESETS
                .get(RANDOM.nextInt(SoupPrompt.SOUP_PRESETS.size()));
        SoupGame game = new SoupGame();
        game.setGameId(gameId);
        game.setUserId(userId);
        game.setConversationId(UUID.randomUUID().toString());
        game.setSurface(soup[0]);
        game.setSecret(soup[1]);
        game.setStatus("playing");
        soupGameMapper.insert(game);
        return Map.of("gameId", gameId, "surface", soup[0]);
    }

    @Override
    public String ask(Long userId, String gameId, String question) {
        SoupGame game = requireOwnedGame(userId, gameId);
        if (!"playing".equals(game.getStatus())) return ENDED_TIP;

        String content = soupChatClient.prompt()
                .system(SoupPrompt.HOST_PROMPT.replace(SoupPrompt.SECRET_PLACEHOLDER, game.getSecret()))
                .messages(loadContext(gameId))
                .user(question)
                .call()
                .content();

        saveGameMessage(gameId, "USER", question, false);
        saveGameMessage(gameId, "ASSISTANT", content, false);
        return content;
    }
    private void saveGameMessage(
            String gameId, String type, String content, boolean isGuess) {
        if (content == null) {
            return;
        }

        SoupGameMessage message = new SoupGameMessage();
        message.setGameId(gameId);
        message.setType(type); // 使用 "USER" 或 "ASSISTANT"
        message.setContent(content);
        message.setIsGuess(isGuess);
        soupGameMessageMapper.insert(message);
    }

    //提交还原故事，判断是否正确
    @Override
    public String guess(Long userId, String gameId, String story) {
        SoupGame game = requireOwnedGame(userId, gameId);

        if (!"playing".equals(game.getStatus())) return ENDED_TIP;
        String result = soupChatClient.prompt()
                .system(SoupPrompt.JUDGE_PROMPT.replace(SoupPrompt.SECRET_PLACEHOLDER, game.getSecret()))
                .messages(loadContext(gameId))
                .user(story)
                .call()
                .content();
        saveGameMessage(gameId, "USER", story, true);
        saveGameMessage(gameId, "ASSISTANT", result, false);
        if (result != null && result.contains("[CORRECT]")) {
            updateStatus(userId, gameId, "solved");
        }
        return result;
    }

    @Override
    public String reveal(Long userId, String gameId) {
        SoupGame game = requireOwnedGame(userId, gameId);
        updateStatus(userId, gameId, "revealed");
        return game.getSecret();
    }

    @Override
    public List<Map<String, String>> listSessions(Long userId) {
        return soupGameMapper.selectList(Wrappers.<SoupGame>lambdaQuery()
                        .eq(SoupGame::getUserId, userId)
                        .select(SoupGame::getGameId, SoupGame::getSurface, SoupGame::getStatus)
                        .orderByDesc(SoupGame::getGameId))
                .stream()
                .map(game -> Map.of(
                        "id", game.getGameId(),
                        "title", truncate(game.getSurface()),
                        "status", game.getStatus()))
                .toList();
    }

    // 获取游戏详情
    @Override
    public Map<String, Object> detail(Long userId, String gameId) {
        // 查整局游戏信息，并校验这局属于当前用户
        SoupGame game = requireOwnedGame(userId, gameId);
        String status = game.getStatus();

        // 按消息顺序读取这局的业务消息
        List<Map<String, Object>> messages = soupGameMessageMapper
                .selectList(Wrappers.<SoupGameMessage>lambdaQuery()
                        .eq(SoupGameMessage::getGameId, gameId)
                        .orderByAsc(SoupGameMessage::getId))
                .stream()
                .map(message -> Map.<String, Object>of(
                        "role", "USER".equalsIgnoreCase(message.getType())
                                ? "user"
                                : "assistant",
                        "content", message.getContent(),
                        "isGuess", Boolean.TRUE.equals(message.getIsGuess())
                ))
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("surface", game.getSurface());
        result.put("status", status);
        result.put("secret", "playing".equals(status) ? "" : game.getSecret());
        result.put("messages", messages);
        return result;
    }

    @Override
    public void delete(Long userId, String gameId) {
        SoupGame game = requireOwnedGame(userId, gameId);
        soupGameMessageMapper.delete(Wrappers.<SoupGameMessage>lambdaQuery()
                .eq(SoupGameMessage::getGameId, gameId));
        soupGameMapper.delete(Wrappers.<SoupGame>lambdaQuery()
                .eq(SoupGame::getGameId, gameId)
                .eq(SoupGame::getUserId, userId));
        chatMemoryRepository.deleteByConversationId(game.getConversationId());
    }

    private List<Message> loadContext(String gameId) {
        return soupGameMessageMapper
                .selectList(Wrappers.<SoupGameMessage>lambdaQuery()
                        .eq(SoupGameMessage::getGameId, gameId)
                        .orderByAsc(SoupGameMessage::getId))
                .stream()
                .map(message -> {
                    Message contextMessage = "USER".equalsIgnoreCase(message.getType())
                            ? new UserMessage(message.getContent())
                            : new AssistantMessage(message.getContent());
                    return contextMessage;
                })
                .toList();
    }

    // ===== 私有工具 =====

    private SoupGame requireOwnedGame(Long userId, String gameId) {
        SoupGame game = soupGameMapper.selectOne(Wrappers.<SoupGame>lambdaQuery()
                .eq(SoupGame::getGameId, gameId)
                .eq(SoupGame::getUserId, userId));
        if (game == null) throw new BaseException("游戏不存在或无权访问");
        return game;
    }



    private void updateStatus(Long userId, String gameId, String status) {
        soupGameMapper.update(null, Wrappers.<SoupGame>lambdaUpdate()
                .eq(SoupGame::getGameId, gameId)
                .eq(SoupGame::getUserId, userId)
                .set(SoupGame::getStatus, status));
    }



    private String truncate(String s) {
        return s.length() > 12 ? s.substring(0, 12) + "..." : s;
    }
}
