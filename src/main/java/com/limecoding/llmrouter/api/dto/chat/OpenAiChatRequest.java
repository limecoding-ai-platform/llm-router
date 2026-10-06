package com.limecoding.llmrouter.api.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiTool;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatRequest(
        String model,
        @NotEmpty List<@NotNull @Valid OpenAiMessage> messages,
        List<OpenAiTool> tools,  // 도구 호출
        @JsonProperty("tool_choice") JsonNode toolChoice, // 도구 선택
        @JsonProperty("max_tokens") Integer maxTokens,
        @JsonProperty("temperature") Double temperature,
        Boolean stream
) {
    public OpenAiChatRequest {
        tools = tools == null ? List.of() : tools;
    }

    public record OpenAiMessage(
            String role,
            String content,
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
}
