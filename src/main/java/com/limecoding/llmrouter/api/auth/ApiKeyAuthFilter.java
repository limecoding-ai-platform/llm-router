package com.limecoding.llmrouter.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

public final class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String BEARER = "Bearer ";

    private final List<byte[]> validKeys;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(List<String> apiKeys, ObjectMapper objectMapper) {
        this.validKeys = apiKeys.stream()
                .map(key -> key.getBytes(StandardCharsets.UTF_8))
                .toList();
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String token = extreactBearer(request.getHeader(HttpHeaders.AUTHORIZATION));

        if (token == null || !isValid(token)) {
            log.warn("Unauthorized: {} {} from {}", request.getMethod(),
                    request.getRequestURI(), request.getRemoteAddr());
            writeUnauthorized(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private static String extreactBearer(String header) {
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        String token = header.substring(BEARER.length()).trim();

        return token.isEmpty() ? null : token;
    }

    private boolean isValid(String token) {
        byte[] given = token.getBytes(StandardCharsets.UTF_8);
        boolean matched = false;

        for (byte[] key : validKeys) {
            matched |= MessageDigest.isEqual(given, key);   // 일치해도 멈추지 않고 전부 비교
        }

        return matched;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER.trim());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        var body = Map.of("error", Map.of(
                "message", "Invalid or missing API key",
                "type", "invalid_request_error",
                "code", "invalid_api_key"));

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
