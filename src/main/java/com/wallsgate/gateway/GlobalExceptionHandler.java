package com.wallsgate.gateway;

import com.wallsgate.domain.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Centralized exception handler translating domain and validation exceptions
 * into standardized OpenAI-compatible error payloads.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        return buildError(HttpStatus.UNAUTHORIZED, "unknown_user", ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        return buildError(HttpStatus.UNAUTHORIZED, "unauthorized", ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(InvalidRequestException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "bad_request", ex.getMessage());
    }

    @ExceptionHandler(EthicalWallViolationException.class)
    public ResponseEntity<Map<String, Object>> handleWallViolation(EthicalWallViolationException ex) {
        return buildError(HttpStatus.FORBIDDEN, "ethical_wall", ex.getMessage());
    }

    @ExceptionHandler(TokenBudgetExceededException.class)
    public ResponseEntity<Map<String, Object>> handleBudgetExceeded(TokenBudgetExceededException ex) {
        return buildError(HttpStatus.TOO_MANY_REQUESTS, "budget_exceeded", ex.getMessage());
    }

    @ExceptionHandler(ModelInferenceException.class)
    public ResponseEntity<Map<String, Object>> handleModelError(ModelInferenceException ex) {
        return buildError(HttpStatus.BAD_GATEWAY, "llm_backend_error", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "bad_request", ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        String code = ex.getStatusCode().equals(HttpStatus.UNAUTHORIZED) ? "unauthorized" : "error";
        return buildError(HttpStatus.valueOf(ex.getStatusCode().value()), code, ex.getReason());
    }

    private static ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of(
                        "code", code,
                        "message", message != null ? message : status.getReasonPhrase()
                )
        ));
    }
}
