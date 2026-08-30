package com.limecoding.llmrouter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ProviderClientConfig {

    @Bean
    RestClient openAiRestClient(
            RestClient.Builder builder,
            @Value("${router.providers.openai.api-key}") String apiKey,
            @Value("${router.providers.openai.base-url}") String baseUrl) {

        return builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Bean
    RestClient anthropicRestClient(
            RestClient.Builder builder,
            @Value("${router.providers.anthropic.api-key}") String apiKey,
            @Value("${router.providers.anthropic.base-url}") String baseUrl,
            @Value("${router.providers.anthropic.anthropic-version}") String anthropicVersion
    ) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", anthropicVersion)
                .defaultHeader("content-type", "application/json")
                .build();
    }
}
