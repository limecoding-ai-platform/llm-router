package com.limecoding.llmrouter.provider.openai.wire;

import java.util.List;

public record OpenAiRequest(
        String model,
        List<OpenAiMessage> messages) {
}
