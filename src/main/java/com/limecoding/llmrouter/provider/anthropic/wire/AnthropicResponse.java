package com.limecoding.llmrouter.provider.anthropic.wire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicResponse(
        String model,
        String id,
        String type,
        String role,
        List<Content> content,
        @JsonProperty("stop_reason") String stopReason,
        @JsonProperty("stop_sequence") String stopSequence,
        @JsonProperty("stop_details") String stopDetails,
        Usage usage) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            int index,
            AnthropicMessage message,
            @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("cache_creation_input_tokens") int cacheCreationInputTokens,
            @JsonProperty("cache_read_input_tokens") int cacheReadInputTokens,
            @JsonProperty("cache_creation") CacheCreation cacheCreation,
            @JsonProperty("output_tokens") int outputTokens,
            @JsonProperty("service_tier") String serviceTier,
            @JsonProperty("inference_geo") String inferenceGeo
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
            String type,
            String text
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CacheCreation(
            @JsonProperty("ephemeral_5m_input_tokens") int ephemeral5mInputTokens,
            @JsonProperty("ephemeral_1h_input_tokens") int ephemeral1hInputTokens
    ) {

    }
}
