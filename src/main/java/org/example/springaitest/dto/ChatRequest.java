package org.example.springaitest.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String chatId;
}
