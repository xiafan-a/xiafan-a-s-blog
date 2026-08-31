package com.xiafan.agent.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Logs every HTTP request handled by the app with the same fields blog-api's AOP logging
 * captures (method, uri, client ip, user-agent, request parameters, duration, response status).
 * JSON request bodies are cached and logged; multipart bodies are skipped (like blog-api's
 * AopUtils which ignores MultipartFile args).
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final int MAX_PARAM_LEN = 2000;

    private final ObjectMapper om;

    public RequestLoggingFilter(ObjectMapper om) {
        this.om = om;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        HttpServletRequest wrapped = wrap(request);
        Throwable failure = null;
        try {
            filterChain.doFilter(wrapped, response);
        } catch (ServletException | IOException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            logAround(request, wrapped, response, start, failure);
        }
    }

    private HttpServletRequest wrap(HttpServletRequest request) {
        String contentType = request.getContentType() == null ? "" : request.getContentType();
        if (contentType.toLowerCase().startsWith("multipart/")) {
            return request;
        }
        // cache-limit doubles as the param truncation point (2000 chars)
        return new ContentCachingRequestWrapper(request, MAX_PARAM_LEN);
    }

    private void logAround(HttpServletRequest original, HttpServletRequest wrapped, HttpServletResponse response,
                           long start, Throwable failure) {
        long costMs = (System.nanoTime() - start) / 1_000_000;
        String method = original.getMethod();
        String uri = original.getRequestURI();
        String ip = clientIp(original);
        String ua = original.getHeader("User-Agent");
        String params = captureParams(wrapped);
        if (failure != null) {
            log.error("[API] {} {} ip={} ua={} params={} cost={}ms ERROR: {}",
                    method, uri, ip, ua, params, costMs, failure);
        } else {
            log.info("[API] {} {} status={} ip={} ua={} cost={}ms params={}",
                    method, uri, response.getStatus(), ip, ua, costMs, params);
        }
    }

    private String captureParams(HttpServletRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        try {
            if (request instanceof ContentCachingRequestWrapper cache) {
                byte[] body = cache.getContentAsByteArray();
                String contentType = request.getContentType() == null ? "" : request.getContentType();
                if (body != null && body.length > 0
                        && contentType.toLowerCase().startsWith("application/json")) {
                    params.put("body", new String(body, StandardCharsets.UTF_8));
                }
            }
            for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
                String[] values = e.getValue();
                params.put(e.getKey(), values.length == 1 ? values[0] : List.of(values));
            }
        } catch (Exception ignored) {
            // never let request logging break the actual request
        }
        if (params.isEmpty()) {
            return "";
        }
        try {
            String json = om.writeValueAsString(params);
            return json.length() <= MAX_PARAM_LEN ? json : json.substring(0, MAX_PARAM_LEN);
        } catch (Exception e) {
            String raw = String.valueOf(params);
            return raw.substring(0, Math.min(MAX_PARAM_LEN, raw.length()));
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}