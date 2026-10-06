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
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
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
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(wire)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new RestClientResponseException("OpenAI API returned an error: " + response.getStatusCode(),
                                response.getStatusCode(), response.getStatusText(), response.getHeaders(),
                                response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    }

                    try (var reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        var data = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            if (line.isEmpty()) {
                                if (!data.isEmpty() && emitPayload(data.toString(), sink)) return null;
                                data.setLength(0);
                                continue;
                            }
                            if (!line.startsWith("data:")) continue;
                            String value = line.substring(5);
                            if (value.startsWith(" ")) value = value.substring(1);
                            if (!data.isEmpty()) data.append('\n');
                            data.append(value);
                        }
                        // Some upstreams close immediately after the final data line.
                        if (!data.isEmpty() && emitPayload(data.toString(), sink)) return null;
                        throw new IOException("OpenAI stream ended before [DONE]");
                    }
                });
    }

    private boolean emitPayload(String payload, Consumer<StreamEvent> sink) throws IOException {
        if ("[DONE]".equals(payload.trim())) return true;
        if (payload.isBlank()) return false;
        var json = objectMapper.readTree(payload);
        if (json.hasNonNull("error")) {
            throw new IOException("OpenAI returned an error during streaming");
        }
        var chunk = objectMapper.treeToValue(json, OpenAiChunk.class);
        streamMapper.toEvents(chunk).forEach(sink);
        return false;
    }

    private OpenAiResponse post(OpenAiRequest wire) {
        return restClient.post()
                .uri("/chat/completions")
                .body(wire)
                .retrieve()
                .body(OpenAiResponse.class);
    }
}
