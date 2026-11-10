package com.wallsgate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallsgate.sdk.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class WallsGateClientTest {

    private HttpClient mockHttp;
    private HttpResponse<String> mockResponse;
    private WallsGateClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockHttp = Mockito.mock(HttpClient.class);
        mockResponse = Mockito.mock(HttpResponse.class);
        client = WallsGateClient.builder()
                .baseUrl("http://mock-gateway:8080")
                .httpClient(mockHttp)
                .mapper(mapper)
                .build();
    }

    @Test
    @DisplayName("Parses successful chat completion response")
    void parsesSuccessfulResponse() {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Settlement terms analysed.\"}}]}";
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(json);
        when(mockHttp.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        AgentContext ctx = AgentContext.builder().userId("alice").sessionId("s-alice").build();
        String result = client.complete(ctx, "What are the settlement terms?");

        assertEquals("Settlement terms analysed.", result);
    }

    @Test
    @DisplayName("Translates 403 response into typed WallViolationException")
    void maps403ToWallViolationException() {
        String json = "{\"error\":\"ethical_wall\",\"message\":\"Wall violation: M-1001 <-> M-2002\"}";
        when(mockResponse.statusCode()).thenReturn(403);
        when(mockResponse.body()).thenReturn(json);
        when(mockHttp.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        AgentContext ctx = AgentContext.builder().userId("carol").sessionId("s-carol").build();
        WallViolationException ex = assertThrows(WallViolationException.class, () ->
                client.complete(ctx, "Access conflicted documents"));

        assertTrue(ex.getReason().contains("M-1001 <-> M-2002"));
    }

    @Test
    @DisplayName("Translates 401 response into typed GatewayAuthException")
    void maps401ToGatewayAuthException() {
        when(mockResponse.statusCode()).thenReturn(401);
        when(mockResponse.body()).thenReturn("{\"error\":\"unknown_user\"}");
        when(mockHttp.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        AgentContext ctx = AgentContext.builder().userId("mallory").build();
        assertThrows(GatewayAuthException.class, () ->
                client.complete(ctx, "Test prompt"));
    }
}
