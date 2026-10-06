package com.limecoding.llmrouter.core.model;

import tools.jackson.databind.JsonNode;

public record Tool(
        String type,
        Function function
) {

    public record Function(
            String name,
            String description,
            JsonNode parameters,
            Boolean strict
    ) {
    }
}


