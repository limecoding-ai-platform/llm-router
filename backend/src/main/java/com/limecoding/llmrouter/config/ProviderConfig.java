package com.limecoding.llmrouter.config;

import com.limecoding.llmrouter.config.properties.AnthropicProperties;
import com.limecoding.llmrouter.config.properties.OpenAiProperties;
import com.limecoding.llmrouter.core.CatalogModel;
import com.limecoding.llmrouter.core.ModelCatalog;
import com.limecoding.llmrouter.core.ProviderAdapter;
import com.limecoding.llmrouter.core.ProviderRegistry;
import com.limecoding.llmrouter.provider.anthropic.AnthropicAdapter;
import com.limecoding.llmrouter.provider.anthropic.AnthropicMapper;
import com.limecoding.llmrouter.provider.openai.MaxTokensParam;
import com.limecoding.llmrouter.provider.openai.OpenAiAdapter;
import com.limecoding.llmrouter.provider.openai.OpenAiMapper;
import com.limecoding.llmrouter.provider.openai.OpenAiStreamMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class ProviderConfig {
    @Bean
    ProviderRegistry providerRegistry(
            RouterProperties props,
            RestClient.Builder builder,
            ObjectMapper objectMapper
    ) {
        var providers = props.providers();
        var adapters = new ArrayList<ProviderAdapter>();

        if (providers.openai() != null) adapters.add(openAiAdapter(providers.openai(), builder.clone(), objectMapper));
        if (providers.anthropic() != null)
            adapters.add(anthropicAdapter(providers.anthropic(), builder.clone(), objectMapper));

        if (adapters.isEmpty()) {
            throw new IllegalStateException("router.providers에 최소 하나의 provider를 설정해야 합니다");
        }

        return new ProviderRegistry(adapters);
    }

    @Bean
    ModelCatalog modelCatalog(RouterProperties props) {
        var providers = props.providers();
        var models = new ArrayList<CatalogModel>();

        if (providers.openai() != null) {
            providers.openai().models().forEach(model -> models.add(
                    catalogModel("openai", model.id(), model.created(), model.ownedBy(), model.shutdownDate())));
        }

        if (providers.anthropic() != null) {
            providers.anthropic().models().forEach(model -> models.add(
                    catalogModel("anthropic", model.id(), 0L, null, null)));
        }

        return new ModelCatalog(models);
    }


    private static ProviderAdapter openAiAdapter(OpenAiProperties props, RestClient.Builder builder, ObjectMapper objectMapper) {
        var client = builder
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .build();

        Map<String, MaxTokensParam> dialects = props.models().stream()
                .filter(model -> model.maxTokensParam() != null)
                .collect(Collectors.toUnmodifiableMap(
                        OpenAiProperties.Model::id,
                        OpenAiProperties.Model::maxTokensParam));

        return new OpenAiAdapter(
                new OpenAiMapper(dialects),
                new OpenAiStreamMapper(),
                objectMapper,
                client,
                props.models().stream().collect(Collectors.toUnmodifiableMap(
                        OpenAiProperties.Model::id, OpenAiProperties.Model::supportedApis)));

    }

    private static ProviderAdapter anthropicAdapter(AnthropicProperties props, RestClient.Builder builder, ObjectMapper objectMapper) {
        var client = builder
                .baseUrl(props.baseUrl())
                .defaultHeader("x-api-key", props.apiKey())
                .defaultHeader("anthropic-version", props.anthropicVersion())
                .defaultHeader("content-type", "application/json")
                .build();

        return new AnthropicAdapter(
                new AnthropicMapper(objectMapper),
                objectMapper,
                client
        );
    }

    private static CatalogModel catalogModel(String provider, String id, long created,
                                             String ownedBy, LocalDate shutdownDate) {
        return new CatalogModel(provider, id, created, ownedBy != null ? ownedBy : provider, shutdownDate);
    }
}
