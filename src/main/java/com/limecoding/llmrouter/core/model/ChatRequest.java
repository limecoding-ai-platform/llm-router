package com.limecoding.llmrouter.core.model;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record ChatRequest(
        String model,
        List<Message> messages,
        List<Tool> tools,
        JsonNode toolChoice,
        Integer maxTokens,
        Double temperature,
        boolean includeUsage) {
    public ChatRequest(String model, List<Message> messages, List<Tool> tools,
                       JsonNode toolChoice, Integer maxTokens, Double temperature) {
        this(model, messages, tools, toolChoice, maxTokens, temperature, false);
    }

    public ChatRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be null or empty");
        }
        tools = tools == null ? List.of() : tools;
    }
}
