package com.limecoding.llmrouter.provider.anthropic;

import com.limecoding.llmrouter.core.ProviderAdapter;
import com.limecoding.llmrouter.core.model.ChatRequest;
import com.limecoding.llmrouter.core.model.ChatResponse;
import com.limecoding.llmrouter.core.model.StreamEvent;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicRequest;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicResponse;
import com.limecoding.llmrouter.provider.anthropic.wire.chunk.AnthropicEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AnthropicAdapter implements ProviderAdapter {
    private final AnthropicMapper mapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AnthropicAdapter(
            AnthropicMapper mapper,
            ObjectMapper objectMapper,
            @Qualifier("anthropicRestClient") RestClient restClient) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public String id() {
        return "anthropic";
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        AnthropicRequest wire = mapper.toWire(request);
        AnthropicResponse post = post(wire);

        return mapper.toDomain(post);
    }

    @Override
    public void stream(ChatRequest domain, Consumer<StreamEvent> sink) {
        AnthropicStreamMapper streamMapper = new AnthropicStreamMapper();

        AnthropicRequest wire = mapper.toWire(domain, true);
        restClient.post()
                .uri("/messages")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(wire)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new RuntimeException("Anthropic API returned an error: " + response.getStatusCode());
                    }

                    try (var reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;

                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank() || line.startsWith(":")) continue;
                            if (!line.startsWith("data:")) continue;

                            String payload = line.substring(5).stripLeading();
                            var chunk = objectMapper.readValue(payload, AnthropicEvent.class);

                            streamMapper.toEvents(chunk).forEach(sink);
                        }
                    }

                    return null;
                });
    }

    private AnthropicResponse post(AnthropicRequest wire) {
        return restClient.post()
                .uri("/messages")
                .body(wire)
                .retrieve()
                .body(AnthropicResponse.class);

    }
}
