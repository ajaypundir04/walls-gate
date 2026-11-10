package com.wallsgate.service.dto;

import java.util.List;

public record ChatCommand(
        String userId,
        String sessionHeader,
        String traceHeader,
        String model,
        List<ChatMessage> messages
) {
    public record ChatMessage(String role, String content) {}
}
