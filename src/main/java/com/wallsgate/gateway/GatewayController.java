package com.wallsgate.gateway;

import com.wallsgate.config.OpenApiConfig;
import com.wallsgate.service.GatewayService;
import com.wallsgate.service.dto.ChatCommand;
import com.wallsgate.service.dto.ChatCommand.ChatMessage;
import com.wallsgate.service.dto.ChatResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Gateway", description = "OpenAI-compatible chat completions with ethical wall enforcement and DLP redaction")
public class GatewayController {

    @Schema(description = "Individual chat message")
    public record Msg(
            @Schema(description = "Message role (system, user, assistant)", example = "user") String role,
            @Schema(description = "Message content text", example = "Summarise Acme litigation strategy") String content
    ) {}

    @Schema(description = "OpenAI-compatible chat completion request payload")
    public record ChatRequest(
            @Schema(description = "Model identifier to invoke", example = "local") String model,
            @Schema(description = "List of conversation messages") List<Msg> messages
    ) {}

    private final GatewayService gatewayService;

    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping("/v1/chat/completions")
    @Operation(
            summary = "Create chat completion with ethical wall enforcement",
            description = "Processes a multi-turn chat request against permission-aware retrieval documents. "
                    + "Enforces token budgets, filters documents by caller ACLs, evaluates ethical wall conflicts, "
                    + "redacts sensitive PII (emails, phones, IBANs), and queries the local LLM backend.",
            security = {
                    @SecurityRequirement(name = OpenApiConfig.SECURITY_USER_ID),
                    @SecurityRequirement(name = OpenApiConfig.SECURITY_SESSION_ID),
                    @SecurityRequirement(name = OpenApiConfig.SECURITY_TRACE_ID)
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Successful chat completion with ethical wall metadata",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"id\":\"chatcmpl-123\",\"object\":\"chat.completion\",\"model\":\"local\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Settlement overview...\"},\"finish_reason\":\"stop\"}],\"walls_gate\":{\"documents\":[\"doc-101\"],\"matters\":[\"M-1001\"],\"shadow_mode\":false}}"
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or empty messages"),
            @ApiResponse(responseCode = "401", description = "Missing or unregistered X-User-Id caller"),
            @ApiResponse(responseCode = "403", description = "BLOCKED due to ethical wall conflict between queried matters"),
            @ApiResponse(responseCode = "429", description = "Token or character budget exceeded for the session"),
            @ApiResponse(responseCode = "502", description = "Downstream LLM inference backend connection failure")
    })
    public ResponseEntity<Map<String, Object>> chat(
            @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, description = "Caller user identity for ACL retrieval scope", example = "alice", required = false)
            @RequestHeader(value = "X-User-Id", required = false) String userId,

            @Parameter(name = "X-Session-Id", in = ParameterIn.HEADER, description = "Conversation session identifier for tracking ethical walls", example = "sess-42", required = false)
            @RequestHeader(value = "X-Session-Id", required = false) String sessionHeader,

            @Parameter(name = "X-Trace-Id", in = ParameterIn.HEADER, description = "Distributed correlation trace ID for observability", example = "tr-abc123xyz", required = false)
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
