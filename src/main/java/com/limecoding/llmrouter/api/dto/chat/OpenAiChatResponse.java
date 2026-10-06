package com.limecoding.llmrouter.api.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenAiChatResponse(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices,
        Usage usage) {
    public record Choice(
            int index,
            OpenAIChatMessage message,
            @JsonProperty("finish_reason") String finishReason) {
    }

    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens) {
    }

    public record OpenAIChatMessage(
            String role,
            String content,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            @JsonProperty("tool_calls") List<ToolCall> toolCalls) {
    }

    public record ToolCall(String id, String type, FunctionCall function) {
    }

    public record FunctionCall(String name, String arguments) {
    }
}
