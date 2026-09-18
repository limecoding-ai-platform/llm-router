package com.limecoding.llmrouter.api;

import com.limecoding.llmrouter.api.dto.error.OpenAiErrorResponse;
import com.limecoding.llmrouter.core.model.InvalidModelRefException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidModelRefException.class)
    public ResponseEntity<OpenAiErrorResponse> handleInvalidModel(InvalidModelRefException e) {
        return ResponseEntity.badRequest()
                .body(OpenAiErrorResponse.of(
                        e.getMessage(), "invalid_request_error", "model", "model_not_found"));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<String> handleUpstream(RestClientResponseException e) {
        log.warn("Upstream error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());

        return ResponseEntity.status(e.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(e.getResponseBodyAsString());
    }
}