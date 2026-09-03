package com.xiafan.agent.service.agent;

public class CapabilityServiceException extends RuntimeException {

    private final int statusCode;

    public CapabilityServiceException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
