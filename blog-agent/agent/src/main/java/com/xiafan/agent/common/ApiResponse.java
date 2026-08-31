package com.xiafan.agent.common;

/**
 * Unified API response wrapper, mirrors util/response.py ApiResponse.
 */
public record ApiResponse<T>(String code, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("200", data);
    }
}