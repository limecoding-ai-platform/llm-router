package com.limecoding.llmrouter.provider.openai.wire;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiRequest(
        String model,       // 모델
        List<OpenAiMessage> messages,   // 메시지
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<OpenAiTool> tools,  // 도구 호출
        @JsonProperty("tool_choice") JsonNode toolChoice, // 도구 선택
        @JsonProperty("max_tokens") Integer maxTokens,  // 최대 토큰 수
        @JsonProperty("max_completion_tokens") Integer maxCompletionTokens, // 최대 완료 토큰 수
        boolean stream,  // 스트림 여부
        @JsonProperty("stream_options") StreamOptions streamOptions
) {
    public record StreamOptions(@JsonProperty("include_usage") boolean includeUsage) {
    }
}
