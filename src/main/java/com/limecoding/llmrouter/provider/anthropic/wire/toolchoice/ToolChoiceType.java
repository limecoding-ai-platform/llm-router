package com.limecoding.llmrouter.provider.anthropic.wire.toolchoice;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ToolChoiceType {
    AUTO("auto"),
    ANY("any"),
    TOOL("tool"),
    NONE("none");

    private final String type;

    ToolChoiceType(String type) {
        this.type = type;
    }

    @JsonValue
    public String value() {
        return type;
    }
}
