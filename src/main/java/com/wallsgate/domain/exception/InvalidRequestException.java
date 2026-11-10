package com.wallsgate.domain.exception;

public class InvalidRequestException extends DomainException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
