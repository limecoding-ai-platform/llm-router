package com.limecoding.llmrouter.api.mapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionChunkResponse(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices,
        Usage usage
) {
    public record Choice(
            Integer index,
            Delta delta,
            @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Delta(String role, String content, String refusal,
                        @JsonProperty("tool_calls") List<ToolCall> toolCalls) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCall(int index, String id, String type, FunctionDelta function) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionDelta(String name, String arguments) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens,
            @JsonProperty("prompt_tokens_details") PromptTokensDetails promptTokensDetails,
            @JsonProperty("completion_tokens_details") CompletionTokensDetails completionTokensDetails) {
    }

    public record PromptTokensDetails(@JsonProperty("cached_tokens") int cachedTokens) {
    }

    public record CompletionTokensDetails(@JsonProperty("reasoning_tokens") int reasoningTokens) {
    }
}
