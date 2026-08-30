package com.limecoding.llmrouter.core;

import com.limecoding.llmrouter.core.model.ChatRequest;
import com.limecoding.llmrouter.core.model.ChatResponse;
import com.limecoding.llmrouter.core.model.StreamEvent;

import java.util.function.Consumer;

public interface ProviderAdapter {
    String id();

    ChatResponse chat(ChatRequest request);

    void stream(ChatRequest domain, Consumer<StreamEvent> sink);
}
