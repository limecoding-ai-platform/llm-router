package com.limecoding.llmrouter.provider.openai.wire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChunk(
        String id,
        String object,
        Long created,
        String model,
        List<Choice> choices,
        Usage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            Integer index,
            Delta delta,
            @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Delta(
            String role,
            String content,
            String refusal,
            @JsonProperty("tool_calls") List<ToolCallDelta> toolCalls
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCallDelta(
            Integer index,
            String id,
            String type,
            FunctionDelta function
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FunctionDelta(
            String name,
            String arguments
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens,
            @JsonProperty("prompt_tokens_details") PromptTokensDetails promptTokensDetails,
            @JsonProperty("completion_tokens_details") CompletionTokensDetails completionTokensDetails
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PromptTokensDetails(
            @JsonProperty("cached_tokens") Integer cachedTokens
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompletionTokensDetails(
            @JsonProperty("reasoning_tokens") Integer reasoningTokens
    ) {

    }
}
