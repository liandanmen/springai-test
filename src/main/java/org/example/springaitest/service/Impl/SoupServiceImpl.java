package org.example.springaitest.service.Impl;

import lombok.RequiredArgsConstructor;
import org.example.springaitest.constant.SoupPrompt;
import org.example.springaitest.service.SoupService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class SoupServiceImpl implements SoupService {

    private final JdbcTemplate jdbcTemplate;
    private final ChatClient chatClient;
    private final ChatMemoryRepository chatMemoryRepository;

    private static final Random RANDOM = new Random();
    private static final String ENDED_TIP = "本局已结束，请新开一局。";

    @Override
    public Map<String, String> start(String gameId) {
        String[] soup = SoupPrompt.SOUP_PRESETS
                .get(RANDOM.nextInt(SoupPrompt.SOUP_PRESETS.size()));
        jdbcTemplate.update(
                "INSERT INTO SOUP_GAME (game_id, surface, secret, status) VALUES (?, ?, ?, 'playing')",
                gameId, soup[0], soup[1]);
        return Map.of("gameId", gameId, "surface", soup[0]);
    }

    @Override
    public String ask(String gameId, String question) {
        if (!isPlaying(gameId)) return ENDED_TIP;
        String secret = getSecret(gameId);
        return chatClient.prompt()
                .system(SoupPrompt.HOST_PROMPT.replace(SoupPrompt.SECRET_PLACEHOLDER, secret))
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, gameId))
                .call()
                .content();
    }

    @Override
    public String guess(String gameId, String story) {
        if (!isPlaying(gameId)) return ENDED_TIP;
        String secret = getSecret(gameId);
        String result = chatClient.prompt()
                .system(SoupPrompt.JUDGE_PROMPT.replace(SoupPrompt.SECRET_PLACEHOLDER, secret))
                .user(story)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, gameId))
                .call()
                .content();
        if (result != null && result.contains("[CORRECT]")) {
            jdbcTemplate.update("UPDATE SOUP_GAME SET status='solved' WHERE game_id=?", gameId);
        }
        return result;
    }

    @Override
    public String reveal(String gameId) {
        jdbcTemplate.update("UPDATE SOUP_GAME SET status='revealed' WHERE game_id=?", gameId);
        return getSecret(gameId);
    }

    @Override
    public List<Map<String, String>> listSessions() {
        return jdbcTemplate.queryForList(
                        "SELECT game_id, surface, status FROM SOUP_GAME ORDER BY game_id DESC")
                .stream()
                .map(row -> Map.of(
                        "id", String.valueOf(row.get("game_id")),
                        "title", truncate(String.valueOf(row.get("surface"))),
                        "status", String.valueOf(row.get("status"))))
                .toList();
    }

    @Override
    public Map<String, Object> detail(String gameId) {
        Map<String, Object> game = jdbcTemplate.queryForMap(
                "SELECT surface, status, secret FROM SOUP_GAME WHERE game_id=?", gameId);
        String status = String.valueOf(game.get("status"));

        List<Map<String, String>> messages = chatMemoryRepository
                .findByConversationId(gameId).stream()
                .map(m -> Map.of(
                        "role", m.getMessageType().getValue(),
                        "content", m.getText()))
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("surface", game.get("surface"));
        result.put("status", status);
        result.put("secret", "playing".equals(status) ? "" : game.get("secret"));
        result.put("messages", messages);
        return result;
    }

    @Override
    public void delete(String gameId) {
        jdbcTemplate.update("DELETE FROM SOUP_GAME WHERE game_id=?", gameId);
        chatMemoryRepository.deleteByConversationId(gameId);
    }

    // ===== 私有工具 =====

    private boolean isPlaying(String gameId) {
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM SOUP_GAME WHERE game_id=?", String.class, gameId);
        return "playing".equals(status);
    }

    private String getSecret(String gameId) {
        return jdbcTemplate.queryForObject(
                "SELECT secret FROM SOUP_GAME WHERE game_id=?", String.class, gameId);
    }

    private String truncate(String s) {
        return s.length() > 12 ? s.substring(0, 12) + "..." : s;
    }
}
