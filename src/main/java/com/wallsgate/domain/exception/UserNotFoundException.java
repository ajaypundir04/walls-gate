package com.wallsgate.domain.exception;

public class UserNotFoundException extends DomainException {
    public UserNotFoundException(String userId) {
        super("Unknown or missing X-User-Id: " + (userId == null ? "<null>" : userId));
    }
}
