package com.limecoding.llmrouter.api.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiTool;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
        Boolean stream,
        @JsonProperty("stream_options") StreamOptions streamOptions
) {
    public OpenAiChatRequest {
        tools = tools == null ? List.of() : tools;
    }

    public record StreamOptions(@JsonProperty("include_usage") Boolean includeUsage) {
    }

    public record OpenAiMessage(
            String role,
            String content,
            @JsonProperty("tool_calls") List<ToolCall> toolCalls,
            String refusal,
            List<JsonNode> annotations,
            @JsonProperty("tool_call_id") String toolCallId
    ) {
        public OpenAiMessage {
            toolCalls = toolCalls == null ? List.of() : toolCalls;
        }

        @JsonIgnore
        @AssertTrue(message = "tool_call_id must not be blank for tool messages")
        public boolean isToolCallIdValid() {
            return !"tool".equalsIgnoreCase(role) || (toolCallId != null && !toolCallId.isBlank());
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
