package com.wallsgate;

import com.wallsgate.model.Models.Wall;
import com.wallsgate.policy.PolicyStore;
import com.wallsgate.policy.WallEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WallEngineTest {

    private PolicyStore store;
    private WallEngine wallEngine;

    @BeforeEach
    void setUp() {
        store = new PolicyStore();
        store.putWall(new Wall("M-1001", "M-2002"));
        wallEngine = new WallEngine(store);
    }

    @Test
    @DisplayName("Single matter access has no conflicts")
    void singleMatterAllowed() {
        WallEngine.Decision decision = wallEngine.evaluate("session-1", Set.of("M-1001"));
        assertFalse(decision.violation());
        assertTrue(decision.conflicts().isEmpty());
    }

    @Test
    @DisplayName("Touching two walled matters in same request triggers violation")
    void simultaneousWalledMattersBlocked() {
        WallEngine.Decision decision = wallEngine.evaluate("session-carol", Set.of("M-1001", "M-2002"));
        assertTrue(decision.violation());
        assertEquals(1, decision.conflicts().size());
        assertEquals("M-1001 <-> M-2002", decision.conflicts().get(0));
    }

    @Test
    @DisplayName("Touching walled matter across multiple requests in same session is blocked")
    void sequentialWalledMattersBlocked() {
        // Step 1: User touches M-1001 and commits
        WallEngine.Decision d1 = wallEngine.evaluate("session-seq", Set.of("M-1001"));
        assertFalse(d1.violation());
        wallEngine.commit("session-seq", Set.of("M-1001"));

        // Step 2: Same session touches harmless M-3003
        WallEngine.Decision d2 = wallEngine.evaluate("session-seq", Set.of("M-3003"));
        assertFalse(d2.violation());
        wallEngine.commit("session-seq", Set.of("M-3003"));

        // Step 3: Same session tries to touch M-2002 -> BLOCKED because session already touched M-1001
        WallEngine.Decision d3 = wallEngine.evaluate("session-seq", Set.of("M-2002"));
        assertTrue(d3.violation());
        assertEquals("M-1001 <-> M-2002", d3.conflicts().get(0));
    }

    @Test
    @DisplayName("Different sessions remain isolated")
    void differentSessionsAreIsolated() {
        wallEngine.commit("session-alice", Set.of("M-1001"));
        wallEngine.commit("session-bob", Set.of("M-2002"));

        // Alice session touching M-3003 is fine
        assertFalse(wallEngine.evaluate("session-alice", Set.of("M-3003")).violation());
        // Bob session touching M-3003 is fine
        assertFalse(wallEngine.evaluate("session-bob", Set.of("M-3003")).violation());
    }

    @Test
    @DisplayName("Concurrent evaluations and commits are thread-safe")
    void concurrentSessionEvaluation() throws InterruptedException {
        int threads = 16;
        int iterations = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger violationsDetected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            pool.submit(() -> {
                try {
                    String sessionId = "concurrent-session-" + (threadId % 4);
                    for (int j = 0; j < iterations; j++) {
                        Set<String> matters = (j % 2 == 0) ? Set.of("M-1001") : Set.of("M-2002");
                        WallEngine.Decision d = wallEngine.evaluate(sessionId, matters);
                        if (d.violation()) {
                            violationsDetected.incrementAndGet();
                        } else {
                            wallEngine.commit(sessionId, matters);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
        assertTrue(violationsDetected.get() > 0, "Conflicts should be detected when same session touches both matters");
    }
}
