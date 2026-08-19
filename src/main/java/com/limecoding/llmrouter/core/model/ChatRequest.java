package com.limecoding.llmrouter.core.model;

import java.util.List;

public record ChatRequest(
        String model,
        List<Message> messages,
        Integer maxTokens,
        Double temperature) {
}
