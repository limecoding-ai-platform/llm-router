package com.limecoding.llmrouter.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenAiChatRequest(
        String model,
        String provider,
        List<OpenAiMessage> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        @JsonProperty("temperature") Double temperature
) {
    public record OpenAiMessage(
            String role,
            String content) {
    }
}
