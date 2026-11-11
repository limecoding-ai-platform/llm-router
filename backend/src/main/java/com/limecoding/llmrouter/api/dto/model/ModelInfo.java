package com.limecoding.llmrouter.api.dto.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.limecoding.llmrouter.core.CatalogModel;

import java.time.LocalDate;

public record ModelInfo(
        String id,
        String object,
        Long created,
        @JsonProperty("owned_by") String ownedBy,
        @JsonProperty("shutdown_date") LocalDate shutdownDate
) {
    public static ModelInfo from(CatalogModel model) {
        return new ModelInfo(
                model.routingId(),
                "model",
                model.created(),
                model.ownedBy(),
                model.shutdownDate()
        );
    }
}
