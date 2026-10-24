package org.example.springaitest.service;

import java.util.List;
import java.util.Map;

public interface SoupService {
    Map<String, String> start(Long userId, String gameId);
    String ask(Long userId, String gameId, String question);
    String guess(Long userId, String gameId, String story);
    String reveal(Long userId, String gameId);
    List<Map<String, String>> listSessions(Long userId);
    Map<String, Object> detail(Long userId, String gameId);
    void delete(Long userId, String gameId);
}
