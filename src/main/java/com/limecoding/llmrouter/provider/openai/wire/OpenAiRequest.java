package com.limecoding.llmrouter.provider.openai.wire;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenAiRequest(
        String model,
        List<OpenAiMessage> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        boolean stream
) {
}
