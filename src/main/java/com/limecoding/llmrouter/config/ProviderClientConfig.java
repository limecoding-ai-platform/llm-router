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
            @Value("${router.providers.openai.api-url}") String baseUrl) {

        return builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
