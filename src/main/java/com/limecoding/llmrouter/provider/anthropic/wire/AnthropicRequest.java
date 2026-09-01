package com.limecoding.llmrouter.provider.anthropic.wire;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AnthropicRequest(
        String model,
        List<AnthropicMessage> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        boolean stream
) {
}
