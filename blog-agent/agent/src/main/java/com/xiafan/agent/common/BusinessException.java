package com.xiafan.agent.common;

/**
 * Signals an HTTP error to be rendered as {"detail": message} with the given status,
 * mirroring FastAPI's HTTPException.
 */
public class BusinessException extends RuntimeException {

    private final int status;

    public BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}