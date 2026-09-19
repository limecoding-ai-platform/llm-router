package com.limecoding.llmrouter.api;

import com.limecoding.llmrouter.api.dto.model.ModelInfo;
import com.limecoding.llmrouter.api.dto.model.ModelResponse;
import com.limecoding.llmrouter.core.ModelCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/v1/models")
@RestController
public class ModelController {

    private final ModelCatalog catalog;

    public ModelController(ModelCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ModelResponse getModel() {
        var data = catalog.models().stream()
                .map(ModelInfo::from)
                .toList();

        return new ModelResponse("list", data);
    }
}
