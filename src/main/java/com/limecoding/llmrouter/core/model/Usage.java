package com.limecoding.llmrouter.core.model;

public record Usage(
        int promptTokens,
        int completionTokens) {

    public int totalToken() {
        return promptTokens + completionTokens;
    }
}
