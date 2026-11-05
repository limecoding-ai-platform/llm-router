package com.limecoding.llmrouter.provider.anthropic.wire.chunk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import static com.limecoding.llmrouter.provider.anthropic.wire.chunk.AnthropicEvent.*;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true,
        defaultImpl = Unknown.class
)
@JsonSubTypes({
        @Type(value = MessageStart.class, name = "message_start"),
        @Type(value = ContentBlockStart.class, name = "content_block_start"),
        @Type(value = ContentBlockDelta.class, name = "content_block_delta"),
        @Type(value = ContentBlockStop.class, name = "content_block_stop"),
        @Type(value = MessageDelta.class, name = "message_delta"),
        @Type(value = MessageStop.class, name = "message_stop"),
        @Type(value = Ping.class, name = "ping"),
})
public sealed interface AnthropicEvent {

    // ── 이벤트 ─────────────────────────────
    record MessageStart(Message message) implements AnthropicEvent {
    }

    record ContentBlockStart(
            int index,
            @JsonProperty("content_block") ContentBlock contentBlock) implements AnthropicEvent {
    }

    record ContentBlockDelta(int index, Delta delta) implements AnthropicEvent {
    }

    record ContentBlockStop(int index) implements AnthropicEvent {
    }

    record MessageDelta(DeltaMeta delta, Usage usage) implements AnthropicEvent {
    }

    record MessageStop() implements AnthropicEvent {
    }

    record Ping() implements AnthropicEvent {
    }

    record Unknown(String type) implements AnthropicEvent {
    }

    // ── 페이로드 ─────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(
            String model,
            String id,
            String type,
            String role,
            List<String> content,
            @JsonProperty("stop_reason") String stopReason,
            @JsonProperty("stop_sequence") String stopSequence,
            @JsonProperty("stop_details") String stopDetails,
            Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("cache_creation_input_tokens") Integer cacheCreationInputTokens,
            @JsonProperty("cache_read_input_tokens") Integer cacheReadInputTokens,
            @JsonProperty("cache_creation") CacheCreation cacheCreation,
            @JsonProperty("output_tokens") Integer outputTokens,
            @JsonProperty("service_tier") String serviceTier,
            @JsonProperty("inference_geo") String inferenceGeo
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CacheCreation(
            @JsonProperty("ephemeral_5m_input_tokens") Integer ephemeral5mInputTokens,
            @JsonProperty("ephemeral_1h_input_tokens") Integer ephemeral1hInputTokens
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(
            String type,
            String text,
            // tool_use 시작: {"type":"tool_use","id":"toolu_...","name":"get_weather","input":{}}
            String id,
            String name
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Delta(
            String type,
            String text,
            // input_json_delta: 완성된 JSON이 아닌 문자열 조각이므로 JsonNode로 파싱하지 않는다.
            @JsonProperty("partial_json") String partialJson
    ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DeltaMeta(
            @JsonProperty("stop_reason") String stopReason,
            @JsonProperty("stop_sequence") String stopSequence,
            @JsonProperty("stop_details") String stopDetails
    ) {

    }
}
