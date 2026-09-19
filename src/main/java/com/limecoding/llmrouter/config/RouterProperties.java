package com.limecoding.llmrouter.config;

import com.limecoding.llmrouter.config.properties.AnthropicProperties;
import com.limecoding.llmrouter.config.properties.OpenAiProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "router", ignoreUnknownFields = false)
public record RouterProperties(@Valid @NotNull Providers providers) {

    public record Providers(
            @Valid OpenAiProperties openai,
            @Valid AnthropicProperties anthropic
    ) {
    }
}
