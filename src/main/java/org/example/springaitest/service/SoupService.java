package org.example.springaitest.service;

import java.util.List;
import java.util.Map;

public interface SoupService {
    Map<String, String> start(String gameId);
    String ask(String gameId, String question);
    String guess(String gameId, String story);
    String reveal(String gameId);
    List<Map<String, String>> listSessions();
    Map<String, Object> detail(String gameId);
    void delete(String gameId);
}
