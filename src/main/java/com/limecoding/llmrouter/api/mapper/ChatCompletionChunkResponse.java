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

    public record Delta(String role, String content) {
    }

    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {
    }
}