package com.wallsgate.domain.exception;

public class TokenBudgetExceededException extends DomainException {
    public TokenBudgetExceededException(String message) {
        super(message);
    }
}
