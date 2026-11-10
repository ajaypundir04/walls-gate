package com.wallsgate.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Client-facing SDK contract for consuming feature teams.
 * Handles identity propagation, request shaping, and error translation into domain exceptions.
 */
public class WallsGateClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Duration timeout;

    private WallsGateClient(Builder b) {
        this.baseUrl = b.baseUrl.replaceAll("/+$", "");
        this.timeout = b.timeout;
        this.http = b.httpClient != null ? b.httpClient :
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.mapper = b.mapper != null ? b.mapper : new ObjectMapper();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Synchronous blocking completion.
     */
    public String complete(AgentContext context, String prompt) {
        try {
            return completeAsync(context, prompt).get();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof SdkException se) throw se;
            throw new SdkException("Gateway call failed: " + cause.getMessage(), cause);
        }
    }

    /**
     * Non-blocking asynchronous completion using CompletableFuture.
     * Easily interoperable with Kotlin Coroutines (e.g. `await()`) or Java Virtual Threads.
     */
    public CompletableFuture<String> completeAsync(AgentContext context, String prompt) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");

        Map<String, Object> payload = Map.of(
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new SdkException("Failed to serialize request", e));
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-User-Id", context.userId())
                .header("X-Session-Id", context.sessionId())
                .header("X-Trace-Id", context.traceId())
                .POST(HttpRequest.BodyPublishers.ofString(json));

        return http.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(this::handleResponse);
    }

    private String handleResponse(HttpResponse<String> resp) {
        int status = resp.statusCode();
        String body = resp.body();

        if (status == 200) {
            try {
                JsonNode root = mapper.readTree(body);
                return root.path("choices").path(0).path("message").path("content").asText("");
            } catch (Exception e) {
                throw new SdkException("Failed to parse gateway response JSON: " + e.getMessage(), e);
            }
        }

        // Map HTTP status to typed domain exceptions
        String errorMsg = body;
        try {
            JsonNode errJson = mapper.readTree(body);
            if (errJson.has("message")) errorMsg = errJson.path("message").asText();
        } catch (Exception ignored) {}

        if (status == 401) {
            throw new GatewayAuthException("Unauthorized agent identity: " + errorMsg);
        }
        if (status == 403) {
            throw new WallViolationException(errorMsg);
        }
        if (status == 429 || status == 400) {
            throw new BudgetExceededException(errorMsg);
        }

        throw new SdkException("Gateway error HTTP " + status + ": " + errorMsg);
    }

    public static class Builder {
        private String baseUrl = "http://localhost:8080";
        private HttpClient httpClient;
        private ObjectMapper mapper;
        private Duration timeout = Duration.ofSeconds(30);

        public Builder baseUrl(String url) { this.baseUrl = url; return this; }
        public Builder httpClient(HttpClient client) { this.httpClient = client; return this; }
        public Builder mapper(ObjectMapper mapper) { this.mapper = mapper; return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }

        public WallsGateClient build() {
            return new WallsGateClient(this);
        }
    }
}
