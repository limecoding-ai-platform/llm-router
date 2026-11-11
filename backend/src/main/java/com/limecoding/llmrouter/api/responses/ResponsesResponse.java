package com.limecoding.llmrouter.api.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record ResponsesResponse(
        String id, String object, @JsonProperty("created_at") long createdAt,
        String status, String model, List<Map<String, Object>> output,
        Map<String, Object> usage, Object error,
        @JsonProperty("incomplete_details") Map<String, String> incompleteDetails,
        boolean store) {
}
