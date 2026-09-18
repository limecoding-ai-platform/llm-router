package com.limecoding.llmrouter.api.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenAiChatRequest(
        String model,
        List<OpenAiMessage> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        @JsonProperty("temperature") Double temperature,
        Boolean stream
) {
    public record OpenAiMessage(
            String role,
            String content) {
    }
}
