package com.limecoding.llmrouter.provider.anthropic.wire;

public record AnthropicMessage(
        String role,
        String content) {
}
