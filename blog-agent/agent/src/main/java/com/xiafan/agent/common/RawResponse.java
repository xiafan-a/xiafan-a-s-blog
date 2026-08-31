package com.xiafan.agent.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method whose response must NOT be wrapped in {@link ApiResponse}.
 * Mirrors fastApiProject endpoints returning bare dicts (e.g. /agent/tools, /rerank).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RawResponse {
}