package com.limecoding.llmrouter.provider.anthropic;

import com.limecoding.llmrouter.core.ProviderAdapter;
import com.limecoding.llmrouter.core.model.ChatRequest;
import com.limecoding.llmrouter.core.model.ChatResponse;
import com.limecoding.llmrouter.core.model.StreamEvent;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicRequest;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.function.Consumer;

@Component
public class AnthropicAdapter implements ProviderAdapter {
    private final AnthropicMapper mapper;
    private final RestClient restClient;

    public AnthropicAdapter(
            AnthropicMapper mapper,
            @Qualifier("anthropicRestClient") RestClient restClient) {
        this.mapper = mapper;
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
        throw new UnsupportedOperationException("Anthropic 스트리밍은 아직 구현되지 않았다");
    }

    private AnthropicResponse post(AnthropicRequest wire) {
        return restClient.post()
                .uri("/messages")
                .body(wire)
                .retrieve()
                .body(AnthropicResponse.class);

    }
}
