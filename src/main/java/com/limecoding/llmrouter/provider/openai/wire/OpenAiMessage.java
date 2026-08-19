package com.limecoding.llmrouter.provider.openai.wire;

public record OpenAiMessage(
        String role,
        String content) {
}
