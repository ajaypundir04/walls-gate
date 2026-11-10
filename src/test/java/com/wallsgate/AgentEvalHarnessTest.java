package com.wallsgate;

import com.wallsgate.eval.AgentEvalHarness;
import com.wallsgate.eval.AgentEvalHarness.EvalTestCase;
import com.wallsgate.model.Models.*;
import com.wallsgate.policy.PolicyStore;
import com.wallsgate.policy.Redactor;
import com.wallsgate.policy.Retriever;
import com.wallsgate.policy.WallEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgentEvalHarnessTest {

    private PolicyStore store;
    private Retriever retriever;
    private WallEngine walls;
    private Redactor redactor;
    private AgentEvalHarness harness;

    @BeforeEach
    void setUp() {
        store = new PolicyStore();
        store.putMatter(new Matter("M-1001", "Acme Litigation"));
        store.putMatter(new Matter("M-2002", "Beta Acquisition"));
        store.putWall(new Wall("M-1001", "M-2002"));

        store.putUser(new User("alice", Set.of("M-1001")));
        store.putUser(new User("carol", Set.of("M-1001", "M-2002")));

        store.putDoc(new Doc("d1", "M-1001", "Acme Strategy", "Offer 2M. Contact ceo@example.com for approvals."));
        store.putDoc(new Doc("d2", "M-2002", "Beta Memo", "Beta valuation summary."));

        retriever = new Retriever(store);
        walls = new WallEngine(store);
        redactor = new Redactor("", "");
        harness = new AgentEvalHarness(store, retriever, walls, redactor);
    }

    @Test
    @DisplayName("Harness returns 100% compliance when all policy and DLP expectations pass")
    void passesCleanSuite() {
        List<EvalTestCase> suite = List.of(
                new EvalTestCase(
                        "TC-01",
                        "Alice asks about Acme; should be allowed and email redacted",
                        "alice",
                        "s-alice",
                        "Summarise Acme strategy for me",
                        false,
                        List.of("ceo@example.com")
                ),
                new EvalTestCase(
                        "TC-02",
                        "Carol tries to touch conflicted matters; should be blocked by wall",
                        "carol",
                        "s-carol",
                        "Compare Acme strategy with Beta memo",
                        true,
                        List.of()
                )
        );

        AgentEvalHarness.EvalReport report = harness.evaluateSuite(suite);
        assertTrue(report.isSuccess());
        assertEquals(2, report.passedTests());
        assertEquals(100.0, report.complianceScore());
    }

    @Test
    @DisplayName("Harness flags test failure if expected wall block does not trigger")
    void detectsPolicyViolationFailure() {
        List<EvalTestCase> suite = List.of(
                new EvalTestCase(
                        "TC-03",
                        "Expected block on harmless query",
                        "alice",
                        "s-alice-2",
                        "Summarise Acme strategy",
                        true, // intentionally expecting block when Alice is allowed
                        List.of()
                )
        );

        AgentEvalHarness.EvalReport report = harness.evaluateSuite(suite);
        assertFalse(report.isSuccess());
        assertEquals(1, report.failedTests());
        assertTrue(report.failures().get(0).contains("Wall decision mismatch"));
    }
}
