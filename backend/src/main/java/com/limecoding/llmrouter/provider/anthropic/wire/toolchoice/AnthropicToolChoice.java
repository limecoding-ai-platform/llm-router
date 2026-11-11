package com.limecoding.llmrouter.provider.anthropic.wire.toolchoice;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

public record AnthropicToolChoice(
        ToolChoiceType type,
        @JsonInclude(JsonInclude.Include.NON_NULL) String name
) {

    public AnthropicToolChoice {
        Objects.requireNonNull(type, "anthropic tool choice type cannot be null");
        if (type == ToolChoiceType.TOOL && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("anthropic tool choice name is required for type tool");
        }
    }
}
