package com.limecoding.llmrouter.api.dto.error;

public record OpenAiErrorResponse(ErrorDetail error) {
    public record ErrorDetail(String message, String type, String param, String code) {
    }

    public static OpenAiErrorResponse of(String message, String type, String param, String code) {
        return new OpenAiErrorResponse(new ErrorDetail(message, type, param, code));
    }
}
