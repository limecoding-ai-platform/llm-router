package com.limecoding.llmrouter.config.properties;

import com.limecoding.llmrouter.provider.openai.MaxTokensParam;
import com.limecoding.llmrouter.core.model.ApiType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record OpenAiProperties(
        @NotBlank String apiKey,
        @NotBlank String baseUrl,
        @NotEmpty List<@Valid Model> models
) {
    public record Model(
            @NotBlank String id,
            MaxTokensParam maxTokensParam,
            @DefaultValue("0") long created,
            String ownedBy,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shutdownDate,
            @DefaultValue("chat_completions") @NotEmpty Set<@NotNull ApiType> supportedApis
    ) {
        public Model {
            supportedApis = supportedApis == null ? Set.of(ApiType.CHAT_COMPLETIONS) : Set.copyOf(supportedApis);
        }

        @AssertTrue(message = "max-tokens-param is required for chat_completions models")
        public boolean isChatTokensConfigured() {
            return !supportedApis.contains(ApiType.CHAT_COMPLETIONS) || maxTokensParam != null;
        }
    }
}
