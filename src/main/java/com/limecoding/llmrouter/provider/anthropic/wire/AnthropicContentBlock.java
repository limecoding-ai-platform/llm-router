package com.limecoding.llmrouter.provider.anthropic.wire;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.JsonNode;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AnthropicContentBlock.Text.class, name = "text"),
        @JsonSubTypes.Type(value = AnthropicContentBlock.ToolUse.class, name = "tool_use"),
        @JsonSubTypes.Type(value = AnthropicContentBlock.ToolResult.class, name = "tool_result")
})
public sealed interface AnthropicContentBlock {

    record Text(
            String text
    ) implements AnthropicContentBlock {
    }

    record ToolUse(
            String id,
            String name,
            JsonNode input
    ) implements AnthropicContentBlock {
    }

    record ToolResult(
            @JsonProperty("tool_use_id") String toolUseId,
            String content
    ) implements AnthropicContentBlock {
    }
}
