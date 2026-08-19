package com.limecoding.llmrouter.core;

import com.limecoding.llmrouter.core.model.ChatRequest;
import com.limecoding.llmrouter.core.model.ChatResponse;

public interface ProviderAdapter {
    String id();

    ChatResponse chat(ChatRequest request);
}
