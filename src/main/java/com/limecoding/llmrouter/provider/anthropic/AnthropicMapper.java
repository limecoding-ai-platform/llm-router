package com.limecoding.llmrouter.provider.anthropic;

import com.limecoding.llmrouter.core.model.*;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicMessage;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicRequest;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class AnthropicMapper {

    // Anthropic은 max_tokens가 필수라 미지정 시 기본값을 채워준다.
    private static final int DEFAULT_MAX_TOKENS = 1024;

    public AnthropicRequest toWire(ChatRequest chatRequest) {
        return new AnthropicRequest(
                chatRequest.model(),
                convertChatMessagesToAnthropicMessage(chatRequest),
                toMaxTokens(chatRequest.maxTokens()),
                false
        );
    }

    public AnthropicRequest toWire(ChatRequest chatRequest, boolean stream) {
        return new AnthropicRequest(
                chatRequest.model(),
                convertChatMessagesToAnthropicMessage(chatRequest),
                toMaxTokens(chatRequest.maxTokens()),
                stream
        );
    }

    public ChatResponse toDomain(AnthropicResponse wire) {
        return new ChatResponse(
                wire.id(),
                wire.model(),
                Message.assistant(wire.content().getFirst().text()),
                toFinishReason(wire.stopReason()),
                toUsage(wire.usage())
        );
    }

    private List<AnthropicMessage> convertChatMessagesToAnthropicMessage(ChatRequest chatRequest) {
        return chatRequest.messages().stream()
                .map(this::toAnthropicMessage).toList();
    }

    private Integer toMaxTokens(Integer maxTokens) {
        return maxTokens == null ? DEFAULT_MAX_TOKENS : maxTokens;
    }

    private AnthropicMessage toAnthropicMessage(Message message) {
        return new AnthropicMessage(
                message.role().name().toLowerCase(Locale.ROOT),
                message.content());
    }

    private FinishReason toFinishReason(String reason) {
        return switch (reason) {
            case "max_tokens", "model_context_window_exceeded" -> FinishReason.LENGTH;
            case "tool_use" -> FinishReason.TOOL_CALLS;
            case "refusal" -> FinishReason.CONTENT_FILTER;
            case null, default -> FinishReason.STOP;
        };
    }

    private Usage toUsage(AnthropicResponse.Usage usage) {
        return new Usage(usage.inputTokens(), usage.outputTokens());
    }
}
