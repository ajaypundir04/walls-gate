package com.wallsgate.domain.exception;

/**
 * Domain exception thrown when an administrative or user operation
 * lacks valid authorization or fails identity credential verification.
 */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
