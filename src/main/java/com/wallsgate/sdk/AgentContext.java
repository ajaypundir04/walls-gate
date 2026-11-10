package com.wallsgate.sdk;

import java.util.Objects;
import java.util.Set;

/**
 * Context propagated with every agent interaction.
 * Represents the identity layer: the user on whose behalf the agent is executing,
 * active session, and authorized matters.
 */
public record AgentContext(String userId, String sessionId, String traceId, Set<String> authorizedMatters) {

    public AgentContext {
        Objects.requireNonNull(userId, "userId must not be null");
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = userId;
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = "trace-" + java.util.UUID.randomUUID();
        }
        authorizedMatters = (authorizedMatters == null) ? Set.of() : Set.copyOf(authorizedMatters);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userId;
        private String sessionId;
        private String traceId;
        private Set<String> authorizedMatters = Set.of();

        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder authorizedMatters(Set<String> matters) { this.authorizedMatters = matters; return this; }

        public AgentContext build() {
            return new AgentContext(userId, sessionId, traceId, authorizedMatters);
        }
    }
}
