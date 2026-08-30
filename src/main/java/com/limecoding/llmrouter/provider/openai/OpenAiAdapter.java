package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.core.ProviderAdapter;
import com.limecoding.llmrouter.core.model.ChatRequest;
import com.limecoding.llmrouter.core.model.ChatResponse;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiRequest;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiAdapter implements ProviderAdapter {
    private final OpenAiMapper mapper;
    private final RestClient restClient;

    public OpenAiAdapter(
            OpenAiMapper mapper,
            @Qualifier("openAiRestClient") RestClient restClient) {
        this.mapper = mapper;
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

    private OpenAiResponse post(OpenAiRequest wire) {
        return restClient.post()
                .uri("/chat/completions")
                .body(wire)
                .retrieve()
                .body(OpenAiResponse.class);
    }
}
