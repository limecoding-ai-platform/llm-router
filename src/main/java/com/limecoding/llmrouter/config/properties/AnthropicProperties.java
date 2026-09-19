package com.limecoding.llmrouter.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AnthropicProperties(
        @NotBlank String apiKey,
        @NotBlank String baseUrl,
        @NotBlank String anthropicVersion,
        @NotEmpty List<@Valid Model> models
) {
    public record Model(@NotBlank String id) {
    }
}