package com.wallsgate.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Talks to any OpenAI-compatible server (vLLM, Ollama /v1, llama.cpp server).
 * If no base-url is configured, returns a mock answer so the gateway can be demoed offline.
 */
@Component
public class LlmClient {
    private final String baseUrl, model, apiKey;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public LlmClient(ObjectMapper mapper,
                     @Value("${wallsgate.llm.base-url:}") String baseUrl,
                     @Value("${wallsgate.llm.model:local}") String model,
                     @Value("${wallsgate.llm.api-key:}") String apiKey) {
        this.mapper = mapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.apiKey = apiKey;
    }

    public String complete(List<Map<String, String>> messages, List<String> contextTitles) throws Exception {
        if (baseUrl.isEmpty()) {
            return "[mock-llm] Context documents used: " + contextTitles;
        }
        String body = mapper.writeValueAsString(Map.of("model", model, "messages", messages));
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (!apiKey.isBlank()) rb.header("Authorization", "Bearer " + apiKey);
        HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("LLM backend returned HTTP " + resp.statusCode());
        }
        JsonNode root = mapper.readTree(resp.body());
        return root.path("choices").path(0).path("message").path("content").asText("");
    }
}
