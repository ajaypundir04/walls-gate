package com.wallsgate.service.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ChatResult(
        String id,
        String model,
        String answer,
        List<String> documentIds,
        Set<String> matters,
        boolean shadowMode
) {
    public Map<String, Object> toOpenAiResponseBody() {
        return Map.of(
                "id", id,
                "object", "chat.completion",
                "model", model,
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", answer),
                        "finish_reason", "stop"
                )),
                "walls_gate", Map.of(
                        "documents", documentIds,
                        "matters", matters,
                        "shadow_mode", shadowMode
                )
        );
    }
}
