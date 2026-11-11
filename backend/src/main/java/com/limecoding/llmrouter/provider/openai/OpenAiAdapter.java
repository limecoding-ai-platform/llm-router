package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.core.ProviderAdapter;
import com.limecoding.llmrouter.core.model.ChatRequest;
import com.limecoding.llmrouter.core.model.ChatResponse;
import com.limecoding.llmrouter.core.model.StreamEvent;
import com.limecoding.llmrouter.core.model.ApiType;
import com.limecoding.llmrouter.core.model.UnsupportedApiException;
import com.limecoding.llmrouter.provider.openai.responses.OpenAiResponsesMapper;
import com.limecoding.llmrouter.provider.openai.responses.OpenAiResponsesStreamMapper;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OpenAiAdapter implements ProviderAdapter {
    private final OpenAiStreamMapper streamMapper;
    private final OpenAiMapper mapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Map<String, Set<ApiType>> supportedApis;
    private final OpenAiResponsesMapper responsesMapper = new OpenAiResponsesMapper();

    public OpenAiAdapter(
            OpenAiMapper mapper,
            OpenAiStreamMapper streamMapper,
            ObjectMapper objectMapper,
            RestClient restClient) {
        this(mapper, streamMapper, objectMapper, restClient,
                mapper.modelIds().stream().collect(Collectors.toUnmodifiableMap(id -> id, id -> Set.of(ApiType.CHAT_COMPLETIONS))));
    }

    public OpenAiAdapter(OpenAiMapper mapper, OpenAiStreamMapper streamMapper, ObjectMapper objectMapper,
                         RestClient restClient, Map<String, Set<ApiType>> supportedApis) {
        this.mapper = mapper;
        this.streamMapper = streamMapper;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.supportedApis = supportedApis.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    @Override
    public String id() {
        return "openai";
    }

    @Override
    public void validateRequest(ChatRequest request) {
        if (!supportedApis.getOrDefault(request.model(), Set.of()).contains(request.apiType()))
            throw new UnsupportedApiException(request.model(), request.apiType());
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        validateRequest(request);
        if (request.apiType() == ApiType.RESPONSES) {
            var response = restClient.post().uri("/responses").contentType(MediaType.APPLICATION_JSON)
                    .body(responsesMapper.toWire(request, false)).retrieve().body(tools.jackson.databind.JsonNode.class);
            return responsesMapper.toDomain(response);
        }
        var wire = mapper.toWire(request);
        var response = post(wire);

        return mapper.toDomain(response);
    }

    @Override
    public void stream(ChatRequest domain, Consumer<StreamEvent> sink) {
        validateRequest(domain);
        boolean responses = domain.apiType() == ApiType.RESPONSES;
        Object wire = responses ? responsesMapper.toWire(domain, true) : mapper.toWire(domain, true);
        var responsesStream = responses ? new OpenAiResponsesStreamMapper() : null;

        restClient.post()
                .uri(responses ? "/responses" : "/chat/completions")
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
                                if (!data.isEmpty() && emitPayload(data.toString(), sink, responsesStream)) return null;
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
                        if (!data.isEmpty() && emitPayload(data.toString(), sink, responsesStream)) return null;
                        throw new IOException("OpenAI stream ended before " + (responses ? "a terminal response event" : "[DONE]"));
                    }
                });
    }

    private boolean emitPayload(String payload, Consumer<StreamEvent> sink, OpenAiResponsesStreamMapper responsesStream) throws IOException {
        if ("[DONE]".equals(payload.trim())) {
            if (responsesStream != null) throw new IOException("Responses stream requires a terminal response event");
            return true;
        }
        if (payload.isBlank()) return false;
        var json = objectMapper.readTree(payload);
        if (responsesStream != null) return responsesStream.accept(json, sink);
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
