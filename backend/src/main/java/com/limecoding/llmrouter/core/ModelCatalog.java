package com.limecoding.llmrouter.core;

import com.limecoding.llmrouter.core.model.ModelRef;

import java.util.HashSet;
import java.util.List;

public record ModelCatalog(List<CatalogModel> models) {

    public ModelCatalog {
        models = List.copyOf(models);
        var ids = new HashSet<String>();

        for (var model : models) {
            if (!ids.add(model.routingId())) {
                throw new IllegalStateException("중복 모델: " + model.routingId());
            }
        }
    }

    public boolean contains(ModelRef ref) {
        return models.stream().anyMatch(model -> model.routingId().equals(ref.model()));
    }
}
