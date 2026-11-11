package com.limecoding.llmrouter.provider.openai.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** OpenAI upstream DTO; independent of the router's public API contract. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiResponsesRequest(
        String model, List<Map<String, Object>> input,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Map<String, Object>> tools,
        @JsonProperty("tool_choice") Object toolChoice,
        @JsonProperty("max_output_tokens") Integer maxOutputTokens,
        Double temperature, boolean stream, boolean store) {}
