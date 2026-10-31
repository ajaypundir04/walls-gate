package com.wallsgate.gateway;

import com.wallsgate.model.Models.Doc;
import com.wallsgate.model.Models.User;
import com.wallsgate.policy.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenAI-style chat endpoint. Flow:
 * identify user -> permission-aware retrieval -> wall check -> DLP -> model -> DLP -> audit.
 *
 * AUTH NOTE: the MVP trusts the X-User-Id header. Put it behind your reverse proxy / SSO
 * (OIDC) so that only the proxy can set it. Native OIDC is the first post-MVP task.
 */
@RestController
public class GatewayController {
    public record Msg(String role, String content) {}
    public record ChatRequest(String model, List<Msg> messages) {}

    private final PolicyStore store;
    private final Retriever retriever;
    private final WallEngine walls;
    private final Redactor redactor;
    private final LlmClient llm;
    private final AuditService audit;
    private final boolean shadowMode;

    public GatewayController(PolicyStore store, Retriever retriever, WallEngine walls, Redactor redactor,
                             LlmClient llm, AuditService audit,
                             @Value("${wallsgate.shadow-mode:false}") boolean shadowMode) {
        this.store = store; this.retriever = retriever; this.walls = walls;
        this.redactor = redactor; this.llm = llm; this.audit = audit; this.shadowMode = shadowMode;
    }

    @PostMapping("/v1/chat/completions")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionHeader,
            @RequestBody ChatRequest req) {

        Optional<User> maybeUser = userId == null ? Optional.empty() : store.user(userId);
        if (maybeUser.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "unknown_user", "Unknown or missing X-User-Id");
        User user = maybeUser.get();
        String session = (sessionHeader == null || sessionHeader.isBlank()) ? userId : sessionHeader;

        if (req == null || req.messages() == null || req.messages().isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "bad_request", "messages must not be empty");
        }
        String prompt = lastUserMessage(req.messages());
        String promptRedacted = redactor.redact(prompt);

        // 1. permission-aware retrieval: only documents this user may open
        List<Doc> docs = retriever.search(prompt, user.matters());
        Set<String> matters = docs.stream().map(Doc::matterId).collect(Collectors.toCollection(TreeSet::new));
        List<String> docIds = docs.stream().map(Doc::id).toList();

        // 2. wall check across everything this session has touched
        WallEngine.Decision decision = walls.evaluate(session, matters);
        if (decision.violation()) {
            String reason = "Wall violation: " + String.join(", ", decision.conflicts());
            if (!shadowMode) {
                audit.record(userId, session, "BLOCKED", reason, decision.matters(), docIds, promptRedacted);
                return error(HttpStatus.FORBIDDEN, "ethical_wall", reason);
            }
            audit.record(userId, session, "WOULD_BLOCK", reason, decision.matters(), docIds, promptRedacted);
        } else {
            audit.record(userId, session, "ALLOWED", "", decision.matters(), docIds, promptRedacted);
        }
        walls.commit(session, matters);

        // 3. build model input with DLP applied to everything leaving the gateway
        List<Map<String, String>> out = new ArrayList<>();
        StringBuilder ctx = new StringBuilder("Answer using only the context below. If it is insufficient, say so.\n");
        for (Doc d : docs) ctx.append("\n--- ").append(d.title()).append(" ---\n").append(redactor.redact(d.text())).append('\n');
        out.add(Map.of("role", "system", "content", ctx.toString()));
        for (Msg m : req.messages()) {
            if ("system".equals(m.role())) continue; // clients cannot override the gateway system prompt
            out.add(Map.of("role", m.role(), "content", redactor.redact(m.content() == null ? "" : m.content())));
        }

        String answer;
        try {
            answer = redactor.redact(llm.complete(out, docs.stream().map(Doc::title).toList()));
        } catch (Exception e) {
            return error(HttpStatus.BAD_GATEWAY, "llm_backend_error", "Model backend unavailable");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", "chatcmpl-" + UUID.randomUUID());
        body.put("object", "chat.completion");
        body.put("model", req.model() == null ? "local" : req.model());
        body.put("choices", List.of(Map.of("index", 0,
                "message", Map.of("role", "assistant", "content", answer),
                "finish_reason", "stop")));
        body.put("walls_gate", Map.of("documents", docIds, "matters", matters, "shadow_mode", shadowMode));
        return ResponseEntity.ok(body);
    }

    private static String lastUserMessage(List<Msg> msgs) {
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if ("user".equals(msgs.get(i).role()) && msgs.get(i).content() != null) return msgs.get(i).content();
        }
        return "";
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus s, String code, String msg) {
        return ResponseEntity.status(s).body(Map.of("error", Map.of("code", code, "message", msg)));
    }
}
