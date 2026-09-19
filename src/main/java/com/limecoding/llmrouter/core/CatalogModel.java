package com.limecoding.llmrouter.core;

import java.time.LocalDate;

public record CatalogModel(
        String provider,
        String modelId,
        long created,
        String ownedBy,
        LocalDate shutdownDate
) {
    public String routingId() {
        return provider + "/" + modelId;
    }
}
