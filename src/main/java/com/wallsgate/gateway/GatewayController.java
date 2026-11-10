package com.wallsgate.gateway;

import com.wallsgate.service.GatewayService;
import com.wallsgate.service.dto.ChatCommand;
import com.wallsgate.service.dto.ChatCommand.ChatMessage;
import com.wallsgate.service.dto.ChatResult;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Presentation layer controller handling OpenAI-compatible chat completion endpoints.
 * Responsibilities are strictly limited to HTTP protocol concerns, distributed trace
 * context binding (MDC), and delegating to {@link GatewayService}.
 */
@RestController
public class GatewayController {

    public record Msg(String role, String content) {}
    public record ChatRequest(String model, List<Msg> messages) {}

    private final GatewayService gatewayService;

    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping("/v1/chat/completions")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionHeader,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceHeader,
            @RequestBody ChatRequest req) {

        String session = (sessionHeader == null || sessionHeader.isBlank())
                ? (userId != null ? userId : "anon")
                : sessionHeader;
        String traceId = (traceHeader != null && !traceHeader.isBlank())
                ? traceHeader
                : "tr-" + UUID.randomUUID();

        MDC.put("userId", userId != null ? userId : "unknown");
        MDC.put("sessionId", session);
        MDC.put("traceId", traceId);

        try {
            List<ChatMessage> domainMessages = (req != null && req.messages() != null)
                    ? req.messages().stream().map(m -> new ChatMessage(m.role(), m.content())).toList()
                    : List.of();

            ChatCommand command = new ChatCommand(
                    userId,
                    session,
                    traceId,
                    req != null ? req.model() : null,
                    domainMessages
            );

            ChatResult result = gatewayService.processChat(command);
            return ResponseEntity.ok(result.toOpenAiResponseBody());
        } finally {
            MDC.clear();
        }
    }
}
