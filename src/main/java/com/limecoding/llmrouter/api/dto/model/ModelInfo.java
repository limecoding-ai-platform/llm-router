package com.limecoding.llmrouter.api.dto.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ModelInfo(
        String id,
        String object,
        Long created,
        @JsonProperty("owned_by") String ownedBy,
        @JsonProperty("shutdown_date") String shutdownDate
) {

}
