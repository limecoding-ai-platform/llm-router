package com.limecoding.llmrouter.core.model;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record Message(
        Role role,
        String content,
        List<ToolCall> toolCalls,
        String refusal,
        List<JsonNode> annotations,
        String toolCallId
) {
    public Message {
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

    public static Message user(String content) {
        return new Message(
                Role.USER,
                content,
                null,
                null,
                null,
                null
        );
    }

    public static Message assistant(
            String content,
            List<ToolCall> toolCalls,
            String refusal,
            List<JsonNode> annotations
    ) {
        return new Message(
                Role.ASSISTANT,
                content,
                toolCalls,
                refusal,
                annotations,
                null
        );
    }
}
