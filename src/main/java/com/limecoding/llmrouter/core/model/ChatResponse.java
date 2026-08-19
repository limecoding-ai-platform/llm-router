package com.limecoding.llmrouter.core.model;

public record ChatResponse(
        String id,
        String model,
        Message message,
        FinishReason finishReason,
        Usage usage
) {
}
