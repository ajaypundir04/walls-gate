package com.wallsgate.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enforces shared limits on token usage and tool/message output.
 * Ensures an agent or consumer cannot blow up context windows or exhaust model budgets.
 */
@Component
public class TokenBudgetGuard {

    private final int maxPromptChars;
    private final int maxOutputChars;
    private final int sessionCharBudget;
    private final Map<String, AtomicInteger> sessionUsage = new ConcurrentHashMap<>();

    public record BudgetCheck(boolean allowed, String reason, int consumedChars, int remainingChars) {}

    public TokenBudgetGuard(
            @Value("${wallsgate.limits.max-prompt-chars:16000}") int maxPromptChars,
            @Value("${wallsgate.limits.max-output-chars:8000}") int maxOutputChars,
            @Value("${wallsgate.limits.session-char-budget:64000}") int sessionCharBudget) {
        this.maxPromptChars = maxPromptChars;
        this.maxOutputChars = maxOutputChars;
        this.sessionCharBudget = sessionCharBudget;
    }

    /**
     * Checks if a prompt exceeds single-request limits or session aggregate budget.
     */
    public BudgetCheck checkPrompt(String sessionId, String prompt) {
        int length = (prompt == null) ? 0 : prompt.length();

        if (length > maxPromptChars) {
            return new BudgetCheck(false,
                    "Prompt exceeds maximum character limit (" + length + " > " + maxPromptChars + ")",
                    length, 0);
        }

        AtomicInteger usage = sessionUsage.computeIfAbsent(sessionId, k -> new AtomicInteger(0));
        int current = usage.get();
        if (current + length > sessionCharBudget) {
            return new BudgetCheck(false,
                    "Session budget exhausted (" + (current + length) + " > " + sessionCharBudget + ")",
                    current, Math.max(0, sessionCharBudget - current));
        }

        return new BudgetCheck(true, "OK", length, sessionCharBudget - current - length);
    }

    /**
     * Commits consumed characters to session ledger once request succeeds.
     */
    public void consume(String sessionId, int chars) {
        sessionUsage.computeIfAbsent(sessionId, k -> new AtomicInteger(0)).addAndGet(chars);
    }

    /**
     * Truncates or validates tool / output content to ensure downstream consumers don't blow context.
     */
    public String guardOutput(String output) {
        if (output == null) return null;
        if (output.length() <= maxOutputChars) return output;
        return output.substring(0, maxOutputChars) + " [TRUNCATED_BY_SDK_LIMIT]";
    }

    public void resetSession(String sessionId) {
        sessionUsage.remove(sessionId);
    }
}
