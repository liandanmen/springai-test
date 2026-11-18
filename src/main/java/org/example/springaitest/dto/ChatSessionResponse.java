package org.example.springaitest.dto;

import java.util.List;

public record ChatSessionResponse(String id, String title, List<ChatMessageResponse> messages) {
}
