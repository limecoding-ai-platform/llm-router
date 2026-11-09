package com.limecoding.llmrouter.core;

import com.limecoding.llmrouter.core.model.ChatRequest;
import com.limecoding.llmrouter.core.model.ChatResponse;
import com.limecoding.llmrouter.core.model.StreamEvent;

import java.util.function.Consumer;

public interface ProviderAdapter {
    String id();

    /** Called before response headers, and again by adapters before upstream I/O. */
    default void validateRequest(ChatRequest request) {}

    ChatResponse chat(ChatRequest request);

    void stream(ChatRequest domain, Consumer<StreamEvent> sink);
}
