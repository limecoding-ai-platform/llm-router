package com.limecoding.llmrouter.api.dto.model;

import java.util.List;

public record ModelResponse(
        String object,
        List<ModelInfo> data
) {

}
