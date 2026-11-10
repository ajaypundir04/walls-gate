package com.wallsgate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallsgate.gateway.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class AuditServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Records audit events in memory correctly")
    void recordsAuditEventsInMemory() {
        AuditService service = new AuditService(mapper, "");
        service.record("alice", "s-1", "ALLOWED", "", Set.of("M-1001"), List.of("d1"), "Summarise matter");

        List<AuditService.AuditEvent> events = service.all();
        assertEquals(1, events.size());
        AuditService.AuditEvent e = events.get(0);
        assertEquals("alice", e.user());
        assertEquals("s-1", e.session());
        assertEquals("ALLOWED", e.action());
        assertEquals(List.of("M-1001"), e.matters());
        assertEquals(List.of("d1"), e.documents());
    }

    @Test
    @DisplayName("Neutralizes CSV formula injection characters (=, +, -, @)")
    void neutralizesCsvFormulaInjection() {
        AuditService service = new AuditService(mapper, "");
        // An attacker might craft a prompt starting with '=' to trigger Excel dynamic execution
        service.record("attacker", "s-bad", "BLOCKED", "=cmd|' /C calc'!A0",
                Set.of("M-1001"), List.of("d1"), "+SUM(A1:A10)");

        String csv = service.toCsv();
        assertTrue(csv.contains("\"'=cmd|' /C calc'!A0\""), "Must prepend single quote to escape formula injection in reason");
        assertTrue(csv.contains("\"'+SUM(A1:A10)\""), "Must prepend single quote to escape formula injection in prompt");
    }

    @Test
    @DisplayName("Appends audit events to JSONL file when path is configured")
    void appendsAuditEventsToJsonlFile(@TempDir Path tempDir) throws IOException {
        Path auditFile = tempDir.resolve("test-audit.jsonl");
        AuditService service = new AuditService(mapper, auditFile.toString());

        service.record("alice", "s-1", "ALLOWED", "", Set.of("M-1001"), List.of("d1"), "Query 1");
        service.record("carol", "s-2", "BLOCKED", "Wall violation", Set.of("M-1001", "M-2002"), List.of(), "Query 2");

        List<String> lines = Files.readAllLines(auditFile);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("\"user\":\"alice\""));
        assertTrue(lines.get(1).contains("\"action\":\"BLOCKED\""));
    }

    @Test
    @DisplayName("Thread-safe under concurrent logging")
    void concurrentLoggingIsThreadSafe() throws InterruptedException {
        AuditService service = new AuditService(mapper, "");
        int threads = 10;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int tid = i;
            pool.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        service.record("user-" + tid, "s-" + tid, "ALLOWED", "",
                                Set.of("M-" + tid), List.of("d-" + j), "prompt");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
        assertEquals(threads * perThread, service.all().size());
    }
}
