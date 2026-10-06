package com.limecoding.llmrouter.provider.openai.wire;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiMessage(
        String role,
        String content,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,
        String refusal,
        List<JsonNode> annotations
) {
    public OpenAiMessage {
        toolCalls = toolCalls == null ? List.of() : toolCalls;
    }

    public record ToolCall(
            String id,
            String type,
            FunctionCall function
    ) {

    }

    public record FunctionCall(
            String name,
            String arguments
    ) {

    }
}
