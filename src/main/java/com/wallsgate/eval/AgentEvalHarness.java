package com.wallsgate.eval;

import com.wallsgate.model.Models.Doc;
import com.wallsgate.model.Models.User;
import com.wallsgate.policy.PolicyStore;
import com.wallsgate.policy.Redactor;
import com.wallsgate.policy.Retriever;
import com.wallsgate.policy.WallEngine;

import java.util.*;

/**
 * Evaluation Harness to validate Agent SDK security, DLP, and wall policies
 * before a model change or prompt template ships.
 */
public class AgentEvalHarness {

    public record EvalTestCase(
            String id,
            String description,
            String userId,
            String sessionId,
            String prompt,
            boolean expectBlocked,
            List<String> prohibitedPatterns
    ) {}

    public record EvalReport(
            int totalTests,
            int passedTests,
            int failedTests,
            double complianceScore,
            List<String> failures
    ) {
        public boolean isSuccess() {
            return failedTests == 0;
        }
    }

    private final PolicyStore store;
    private final Retriever retriever;
    private final WallEngine walls;
    private final Redactor redactor;

    public AgentEvalHarness(PolicyStore store, Retriever retriever, WallEngine walls, Redactor redactor) {
        this.store = store;
        this.retriever = retriever;
        this.walls = walls;
        this.redactor = redactor;
    }

    public EvalReport evaluateSuite(List<EvalTestCase> suite) {
        int passed = 0;
        List<String> failures = new ArrayList<>();

        for (EvalTestCase tc : suite) {
            Optional<User> userOpt = store.user(tc.userId());
            if (userOpt.isEmpty()) {
                failures.add(tc.id() + ": Test user " + tc.userId() + " not found in policy store");
                continue;
            }
            User user = userOpt.get();

            // 1. Permission retrieval
            List<Doc> docs = retriever.search(tc.prompt(), user.matters());
            Set<String> matters = new TreeSet<>();
            for (Doc d : docs) matters.add(d.matterId());

            // 2. Wall check
            WallEngine.Decision decision = walls.evaluate(tc.sessionId(), matters);
            boolean wasBlocked = decision.violation();

            if (wasBlocked != tc.expectBlocked()) {
                failures.add(String.format("%s: Wall decision mismatch. Expected blocked=%b, actual blocked=%b (conflicts=%s)",
                        tc.id(), tc.expectBlocked(), wasBlocked, decision.conflicts()));
                continue;
            }

            // 3. Redaction verification if allowed
            if (!wasBlocked) {
                String redactedPrompt = redactor.redact(tc.prompt());
                boolean leaked = false;
                for (String prohibited : tc.prohibitedPatterns()) {
                    if (redactedPrompt.toLowerCase().contains(prohibited.toLowerCase())) {
                        failures.add(String.format("%s: PII leak detected in prompt. Prohibited term '%s' was not redacted",
                                tc.id(), prohibited));
                        leaked = true;
                        break;
                    }
                }
                if (leaked) continue;

                // Also verify document context redaction
                for (Doc d : docs) {
                    String redactedDoc = redactor.redact(d.text());
                    for (String prohibited : tc.prohibitedPatterns()) {
                        if (redactedDoc.toLowerCase().contains(prohibited.toLowerCase())) {
                            failures.add(String.format("%s: PII leak detected in doc '%s'. Term '%s' not redacted",
                                    tc.id(), d.id(), prohibited));
                            leaked = true;
                            break;
                        }
                    }
                    if (leaked) break;
                }
                if (leaked) continue;
            }

            passed++;
        }

        int total = suite.size();
        int failed = total - passed;
        double score = total == 0 ? 100.0 : ((double) passed / total) * 100.0;
        return new EvalReport(total, passed, failed, score, failures);
    }
}
