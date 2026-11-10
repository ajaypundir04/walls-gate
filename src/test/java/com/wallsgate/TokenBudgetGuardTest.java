package com.wallsgate;

import com.wallsgate.policy.TokenBudgetGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBudgetGuardTest {

    @Test
    @DisplayName("Allows prompt within single-call and session limits")
    void allowsPromptWithinLimits() {
        TokenBudgetGuard guard = new TokenBudgetGuard(100, 50, 200);
        TokenBudgetGuard.BudgetCheck check = guard.checkPrompt("s-1", "Hello world");
        assertTrue(check.allowed());
        assertEquals("OK", check.reason());
    }

    @Test
    @DisplayName("Blocks prompt exceeding single-call character limit")
    void blocksPromptExceedingLimit() {
        TokenBudgetGuard guard = new TokenBudgetGuard(10, 50, 200);
        TokenBudgetGuard.BudgetCheck check = guard.checkPrompt("s-1", "This prompt is clearly longer than ten characters");
        assertFalse(check.allowed());
        assertTrue(check.reason().contains("exceeds maximum character limit"));
    }

    @Test
    @DisplayName("Blocks prompt when cumulative session budget is exhausted")
    void blocksPromptOnSessionBudgetExhaustion() {
        TokenBudgetGuard guard = new TokenBudgetGuard(100, 50, 50);
        guard.consume("s-1", 40);

        TokenBudgetGuard.BudgetCheck check = guard.checkPrompt("s-1", "Fifteen chars!!");
        assertFalse(check.allowed());
        assertTrue(check.reason().contains("Session budget exhausted"));
    }

    @Test
    @DisplayName("Truncates model or tool output exceeding max output characters")
    void truncatesOutputExceedingLimit() {
        TokenBudgetGuard guard = new TokenBudgetGuard(100, 20, 200);
        String longOutput = "This is a very long model generation response that exceeds twenty chars";
        String guarded = guard.guardOutput(longOutput);

        assertTrue(guarded.contains("[TRUNCATED_BY_SDK_LIMIT]"));
        assertTrue(guarded.startsWith(longOutput.substring(0, 20)));
    }
}
