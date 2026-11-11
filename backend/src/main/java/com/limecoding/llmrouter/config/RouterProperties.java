package com.limecoding.llmrouter.config;

import com.limecoding.llmrouter.config.properties.AnthropicProperties;
import com.limecoding.llmrouter.config.properties.OpenAiProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "router", ignoreUnknownFields = false)
public record RouterProperties(
        @Valid @NotNull Providers providers,
        @Valid @NotNull Auth auth
) {

    public record Providers(
            @Valid OpenAiProperties openai,
            @Valid AnthropicProperties anthropic
    ) {
    }

    public record Auth(@NotEmpty List<@NotBlank String> apiKeys) {
    }
}
