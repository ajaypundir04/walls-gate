package com.wallsgate.sdk;

public class WallViolationException extends SdkException {
    private final String reason;

    public WallViolationException(String reason) {
        super("Ethical wall barrier violation: " + reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
