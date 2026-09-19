package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.core.ProviderAdapter;
import com.limecoding.llmrouter.core.model.ChatRequest;
import com.limecoding.llmrouter.core.model.ChatResponse;
import com.limecoding.llmrouter.core.model.StreamEvent;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiChunk;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiRequest;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class OpenAiAdapter implements ProviderAdapter {
    private final OpenAiStreamMapper streamMapper;
    private final OpenAiMapper mapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiAdapter(
            OpenAiMapper mapper,
            OpenAiStreamMapper streamMapper,
            ObjectMapper objectMapper,
            RestClient restClient) {
        this.mapper = mapper;
        this.streamMapper = streamMapper;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        var wire = mapper.toWire(request);
        var response = post(wire);

        return mapper.toDomain(response);
    }

    @Override
    public void stream(ChatRequest domain, Consumer<StreamEvent> sink) {
        var wire = mapper.toWire(domain, true);

        restClient.post()
                .uri("/chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(wire)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new RuntimeException("OpenAI API returned an error: " + response.getStatusCode());
                    }

                    try (var reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;

                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank() || line.startsWith(":")) continue;
                            if (!line.startsWith("data:")) continue;

                            String payload = line.substring(5).stripLeading();
                            if ("[DONE]".equals(payload)) break;

                            var chunk = objectMapper.readValue(payload, OpenAiChunk.class);
                            streamMapper.toEvents(chunk).forEach(sink);
                        }
                    }

                    return null;
                });
    }

    private OpenAiResponse post(OpenAiRequest wire) {
        return restClient.post()
                .uri("/chat/completions")
                .body(wire)
                .retrieve()
                .body(OpenAiResponse.class);
    }
}
