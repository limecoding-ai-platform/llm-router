package com.limecoding.llmrouter.provider.anthropic.wire;

import java.util.List;

public record AnthropicMessage(
        String role,
        List<AnthropicContentBlock> content) {
    public AnthropicMessage {
        content = List.copyOf(content);
    }
}
