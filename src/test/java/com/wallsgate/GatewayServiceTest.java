package com.wallsgate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallsgate.domain.exception.*;
import com.wallsgate.gateway.AuditService;
import com.wallsgate.gateway.LlmClient;
import com.wallsgate.model.Models.*;
import com.wallsgate.policy.*;
import com.wallsgate.service.GatewayService;
import com.wallsgate.service.dto.ChatCommand;
import com.wallsgate.service.dto.ChatCommand.ChatMessage;
import com.wallsgate.service.dto.ChatResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GatewayServiceTest {

    private PolicyStore policyStore;
    private Retriever retriever;
    private WallEngine wallEngine;
    private Redactor redactor;
    private LlmClient llmClient;
    private AuditService auditService;
    private TokenBudgetGuard tokenGuard;

    @BeforeEach
    void setUp() {
        policyStore = new PolicyStore();
        retriever = new Retriever(policyStore);
        wallEngine = new WallEngine(policyStore);
        redactor = new Redactor("", "");
        llmClient = new LlmClient(new ObjectMapper(), "", "local", "");
        auditService = new AuditService(new ObjectMapper(), "");
        tokenGuard = new TokenBudgetGuard(10000, 8000, 64000);

        // Seed domain models
        policyStore.putMatter(new Matter("M-101", "Matter Alpha"));
        policyStore.putMatter(new Matter("M-202", "Matter Beta"));
        policyStore.putWall(new Wall("M-101", "M-202"));

        policyStore.putUser(new User("partner-alice", Set.of("M-101")));
        policyStore.putUser(new User("partner-charlie", Set.of("M-101", "M-202")));

        policyStore.putDoc(new Doc("doc-alpha", "M-101", "Alpha Settlement", "Settlement details for Alpha"));
        policyStore.putDoc(new Doc("doc-beta", "M-202", "Beta Patent", "Patent infringement claim for Beta"));
    }

    @Test
    @DisplayName("Throws UserNotFoundException when user is missing or unregistered")
    void testUserNotFound() {
        GatewayService service = new GatewayService(policyStore, retriever, wallEngine, redactor, llmClient, auditService, tokenGuard, false);

        ChatCommand missingUser = new ChatCommand(null, "s-1", "t-1", "local", List.of(new ChatMessage("user", "Hello")));
        assertThrows(UserNotFoundException.class, () -> service.processChat(missingUser));

        ChatCommand unknownUser = new ChatCommand("bob-unknown", "s-1", "t-1", "local", List.of(new ChatMessage("user", "Hello")));
        assertThrows(UserNotFoundException.class, () -> service.processChat(unknownUser));
    }

    @Test
    @DisplayName("Throws InvalidRequestException when messages list is empty")
    void testEmptyMessages() {
        GatewayService service = new GatewayService(policyStore, retriever, wallEngine, redactor, llmClient, auditService, tokenGuard, false);

        ChatCommand emptyCmd = new ChatCommand("partner-alice", "s-1", "t-1", "local", List.of());
        assertThrows(InvalidRequestException.class, () -> service.processChat(emptyCmd));
    }

    @Test
    @DisplayName("Successfully processes chat within authorized matter boundary")
    void testSuccessfulChat() {
        GatewayService service = new GatewayService(policyStore, retriever, wallEngine, redactor, llmClient, auditService, tokenGuard, false);

        ChatCommand cmd = new ChatCommand("partner-alice", "sess-alice-1", "tr-100", "local",
                List.of(new ChatMessage("user", "What is the Alpha Settlement status?")));

        ChatResult result = service.processChat(cmd);

        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("local", result.model());
        assertTrue(result.answer().contains("Alpha Settlement"));
        assertTrue(result.documentIds().contains("doc-alpha"));
        assertFalse(result.documentIds().contains("doc-beta")); // Filtered out by user authorization scope!
        assertEquals(Set.of("M-101"), result.matters());
    }

    @Test
    @DisplayName("Blocks chat violating ethical wall in strict mode")
    void testEthicalWallViolationStrictMode() {
        GatewayService service = new GatewayService(policyStore, retriever, wallEngine, redactor, llmClient, auditService, tokenGuard, false);

        // Charlie accesses M-101 first turn
        ChatCommand turn1 = new ChatCommand("partner-charlie", "sess-charlie-1", "tr-201", "local",
                List.of(new ChatMessage("user", "Show me Alpha Settlement")));
        service.processChat(turn1);

        // Charlie queries M-202 in the same session -> Ethical wall violation
        ChatCommand turn2 = new ChatCommand("partner-charlie", "sess-charlie-1", "tr-202", "local",
                List.of(new ChatMessage("user", "Show me Beta Patent")));

        EthicalWallViolationException ex = assertThrows(EthicalWallViolationException.class, () -> service.processChat(turn2));
        assertTrue(ex.getConflicts().contains("M-101 <-> M-202") || ex.getConflicts().contains("M-202 <-> M-101"));

        // Audit check: BLOCKED event recorded
        assertTrue(auditService.all().stream().anyMatch(e -> "BLOCKED".equals(e.action())));
    }

    @Test
    @DisplayName("Allows completion in shadow mode but records WOULD_BLOCK in audit")
    void testEthicalWallShadowMode() {
        // shadowMode = true
        GatewayService service = new GatewayService(policyStore, retriever, wallEngine, redactor, llmClient, auditService, tokenGuard, true);

        // Turn 1
        ChatCommand turn1 = new ChatCommand("partner-charlie", "sess-shadow-1", "tr-301", "local",
                List.of(new ChatMessage("user", "Show me Alpha Settlement")));
        service.processChat(turn1);

        // Turn 2 would violate wall
        ChatCommand turn2 = new ChatCommand("partner-charlie", "sess-shadow-1", "tr-302", "local",
                List.of(new ChatMessage("user", "Show me Beta Patent")));

        ChatResult result = assertDoesNotThrow(() -> service.processChat(turn2));
        assertTrue(result.shadowMode());

        // Audit check: WOULD_BLOCK event recorded
        assertTrue(auditService.all().stream().anyMatch(e -> "WOULD_BLOCK".equals(e.action())));
    }

    @Test
    @DisplayName("Throws TokenBudgetExceededException when prompt exceeds configured budget")
    void testTokenBudgetExceeded() {
        TokenBudgetGuard strictGuard = new TokenBudgetGuard(10, 10, 50); // Very small prompt limit (10 chars)
        GatewayService service = new GatewayService(policyStore, retriever, wallEngine, redactor, llmClient, auditService, strictGuard, false);

        ChatCommand hugeCmd = new ChatCommand("partner-alice", "sess-budget", "tr-400", "local",
                List.of(new ChatMessage("user", "A very long message that exceeds the small token budget limit of 10 chars")));

        assertThrows(TokenBudgetExceededException.class, () -> service.processChat(hugeCmd));
    }
}
