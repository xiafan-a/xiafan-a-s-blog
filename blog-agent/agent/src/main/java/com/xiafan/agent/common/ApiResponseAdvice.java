package com.xiafan.agent.common;

import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;

/**
 * Wraps every non-streaming REST response in {@code {code:"200", data: ...}},
 * mirroring fastApiProject/util/middleware.py ResponseMiddleware.
 */
@RestControllerAdvice(basePackages = "com.xiafan.agent.controller")
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> type = returnType.getParameterType();
        return !ApiResponse.class.isAssignableFrom(type)
                && !SseEmitter.class.isAssignableFrom(type)
                && !isRawResponse(returnType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) {
            return ApiResponse.ok(null);
        }
        return ApiResponse.ok(body);
    }

    private boolean isRawResponse(MethodParameter returnType) {
        Method method = returnType.getMethod();
        if (method != null && AnnotatedElementUtils.hasAnnotation(method, RawResponse.class)) {
            return true;
        }
        Class<?> declaringClass = returnType.getDeclaringClass();
        return declaringClass != null && AnnotatedElementUtils.hasAnnotation(declaringClass, RawResponse.class);
    }
}