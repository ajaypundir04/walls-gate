package com.wallsgate.sdk;

public class SdkException extends RuntimeException {
    public SdkException(String message) { super(message); }
    public SdkException(String message, Throwable cause) { super(message, cause); }
}
