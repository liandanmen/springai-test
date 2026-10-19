package org.example.springaitest.controller;


import lombok.RequiredArgsConstructor;
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
public class ChatHistoryController {
    private final ChatMemoryRepository  chatMemoryRepository;

    @GetMapping("/sessions")
    public List<String> sessions(){
        return chatMemoryRepository.findConversationIds();
    }
    @GetMapping("/{session}")
    public List<Map<String,String>> getChatIds(@PathVariable("session") String session) {
        return chatMemoryRepository.findByConversationId(session).stream().map(message -> Map.of(
                "role", message.getMessageType().getValue(),
                "content", message.getText()
        )).toList();
    }

}
