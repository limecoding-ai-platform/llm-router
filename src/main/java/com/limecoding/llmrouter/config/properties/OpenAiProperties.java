package com.limecoding.llmrouter.config.properties;

import com.limecoding.llmrouter.provider.openai.MaxTokensParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

public record OpenAiProperties(
        @NotBlank String apiKey,
        @NotBlank String baseUrl,
        @NotEmpty List<@Valid Model> models
) {
    public record Model(
            @NotBlank String id,
            @NotNull MaxTokensParam maxTokensParam,
            @DefaultValue("0") long created,
            String ownedBy,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shutdownDate
    ) {
    }
}
