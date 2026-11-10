package com.wallsgate.service;

import com.wallsgate.domain.exception.*;
import com.wallsgate.domain.repository.PolicyRepository;
import com.wallsgate.gateway.AuditService;
import com.wallsgate.gateway.LlmClient;
import com.wallsgate.model.Models.Doc;
import com.wallsgate.model.Models.User;
import com.wallsgate.policy.*;
import com.wallsgate.service.dto.ChatCommand;
import com.wallsgate.service.dto.ChatCommand.ChatMessage;
import com.wallsgate.service.dto.ChatResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core Application Service orchestrating the ethical-wall-aware LLM completion pipeline.
 *
 * <p>Flow:
 * 1. Authenticate user identity and permissions.
 * 2. Validate token budget.
 * 3. Filter-first retrieval within user's allowed matters.
 * 4. Multi-turn ethical wall evaluation.
 * 5. Pre-flight DLP redaction of prompt and context.
 * 6. Local LLM completion.
 * 7. Post-flight output redaction and token consumption.
 * 8. Audit recording and session state commit.
 * </p>
 */
@Service
public class GatewayService {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);

    private final PolicyRepository repository;
    private final Retriever retriever;
    private final WallEngine walls;
    private final Redactor redactor;
    private final LlmClient llm;
    private final AuditService audit;
    private final TokenBudgetGuard tokenGuard;
    private final boolean shadowMode;

    public GatewayService(PolicyRepository repository,
                          Retriever retriever,
                          WallEngine walls,
                          Redactor redactor,
                          LlmClient llm,
                          AuditService audit,
                          TokenBudgetGuard tokenGuard,
                          @Value("${wallsgate.shadow-mode:false}") boolean shadowMode) {
        this.repository = repository;
        this.retriever = retriever;
        this.walls = walls;
        this.redactor = redactor;
        this.llm = llm;
        this.audit = audit;
        this.tokenGuard = tokenGuard;
        this.shadowMode = shadowMode;
    }

    public ChatResult processChat(ChatCommand cmd) {
        // 1. Identify and authenticate user
        if (cmd.userId() == null || cmd.userId().isBlank()) {
            throw new UserNotFoundException(cmd.userId());
        }
        User user = repository.user(cmd.userId())
                .orElseThrow(() -> new UserNotFoundException(cmd.userId()));

        String session = (cmd.sessionHeader() == null || cmd.sessionHeader().isBlank())
                ? cmd.userId()
                : cmd.sessionHeader();

        // 2. Validate input messages
        if (cmd.messages() == null || cmd.messages().isEmpty()) {
            throw new InvalidRequestException("messages must not be empty");
        }
        String prompt = extractLastUserMessage(cmd.messages());

        // 3. Check token budget
        TokenBudgetGuard.BudgetCheck budgetCheck = tokenGuard.checkPrompt(session, prompt);
        if (!budgetCheck.allowed()) {
            log.warn("Budget exceeded for session {}: {}", session, budgetCheck.reason());
            throw new TokenBudgetExceededException(budgetCheck.reason());
        }

        String promptRedacted = redactor.redact(prompt);

        // 4. Permission-aware retrieval: only documents within user's allowed matters
        List<Doc> docs = retriever.search(prompt, user.matters());
        Set<String> matters = docs.stream().map(Doc::matterId).collect(Collectors.toCollection(TreeSet::new));
        List<String> docIds = docs.stream().map(Doc::id).toList();

        // 5. Evaluate multi-turn ethical wall
        WallEngine.Decision decision = walls.evaluate(session, matters);
        if (decision.violation()) {
            String reason = "Wall violation: " + String.join(", ", decision.conflicts());
            if (!shadowMode) {
                log.info("BLOCKED request due to ethical wall violation: {}", reason);
                audit.record(user.id(), session, "BLOCKED", reason, decision.matters(), docIds, promptRedacted);
                throw new EthicalWallViolationException(reason, decision.conflicts(), decision.matters());
            }
            log.info("SHADOW MODE would block request: {}", reason);
            audit.record(user.id(), session, "WOULD_BLOCK", reason, decision.matters(), docIds, promptRedacted);
        } else {
            audit.record(user.id(), session, "ALLOWED", "", decision.matters(), docIds, promptRedacted);
        }
        walls.commit(session, matters);

        // 6. Assemble model input with DLP applied to everything leaving boundary
        List<Map<String, String>> modelInput = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder("Answer using only the context below. If it is insufficient, say so.\n");
        for (Doc d : docs) {
            contextBuilder.append("\n--- ").append(d.title()).append(" ---\n")
                    .append(redactor.redact(d.text())).append('\n');
        }
        modelInput.add(Map.of("role", "system", "content", contextBuilder.toString()));

        for (ChatMessage m : cmd.messages()) {
            if ("system".equalsIgnoreCase(m.role())) continue; // Clients cannot override system instruction
            modelInput.add(Map.of("role", m.role(), "content", redactor.redact(m.content() == null ? "" : m.content())));
        }

        // 7. Invoke model inference
        String rawAnswer;
        try {
            rawAnswer = llm.complete(modelInput, docs.stream().map(Doc::title).toList());
        } catch (Exception e) {
            log.error("Model inference backend error: {}", e.getMessage(), e);
            throw new ModelInferenceException("Model backend unavailable: " + e.getMessage(), e);
        }

        // 8. Output DLP redaction & Token Budget Guard consumption
        String sanitizedAnswer = tokenGuard.guardOutput(redactor.redact(rawAnswer));
        tokenGuard.consume(session, prompt.length() + sanitizedAnswer.length());

        String completionId = "chatcmpl-" + UUID.randomUUID();
        String modelName = (cmd.model() == null || cmd.model().isBlank()) ? "local" : cmd.model();

        return new ChatResult(completionId, modelName, sanitizedAnswer, docIds, matters, shadowMode);
    }

    private static String extractLastUserMessage(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if ("user".equalsIgnoreCase(m.role()) && m.content() != null) {
                return m.content();
            }
        }
        return "";
    }
}
