package com.limecoding.llmrouter.provider.openai.wire;

import tools.jackson.databind.JsonNode;

public record OpenAiTool(
        String type,
        OpenAiFunction function
) {

    public record OpenAiFunction(
            String name,
            String description,
            JsonNode parameters,
            Boolean strict

    ) {
    }
}
