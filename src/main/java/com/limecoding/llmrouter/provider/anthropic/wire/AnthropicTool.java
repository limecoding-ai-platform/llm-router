package com.limecoding.llmrouter.provider.anthropic.wire;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

public record AnthropicTool(
        String name,
        String description,
        @JsonProperty("input_schema") JsonNode inputSchema,
        @JsonProperty("input_example") List<JsonNode> inputExample
) {
}
