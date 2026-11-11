package com.limecoding.llmrouter.core.model;

public record Usage(
        int promptTokens,
        int completionTokens) {

    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}
