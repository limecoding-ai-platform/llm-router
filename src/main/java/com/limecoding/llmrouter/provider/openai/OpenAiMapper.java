package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.core.model.*;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiMessage;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiRequest;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiResponse;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class OpenAiMapper {

    public OpenAiRequest toWire(ChatRequest chatRequest) {
        var messages = chatRequest.messages().stream()
                .map(this::toWire)
                .toList();

        return new OpenAiRequest(
                chatRequest.model(),
                messages,
                chatRequest.maxTokens(),
                false);
    }

    public OpenAiRequest toWire(ChatRequest chatRequest, boolean stream) {
        var messages = chatRequest.messages().stream()
                .map(this::toWire)
                .toList();

        return new OpenAiRequest(
                chatRequest.model(),
                messages,
                chatRequest.maxTokens(),
                stream
        );
    }

    public ChatResponse toDomain(OpenAiResponse wire) {
        var choice = wire.choices().getFirst();

        return new ChatResponse(
                wire.id(),
                wire.model(),
                Message.assistant(choice.message().content()),
                toFinishReason(choice.finishReason()),
                toUsage(wire.usage())
        );
    }

    private Usage toUsage(OpenAiResponse.Usage wire) {
        return new Usage(wire.promptTokens(), wire.completionTokens());
    }

    private OpenAiMessage toWire(Message message) {
        return new OpenAiMessage(
                message.role().name().toLowerCase(Locale.ROOT),
                message.content());
    }

    private FinishReason toFinishReason(String finishReason) {
        return switch (finishReason) {
            case "length" -> FinishReason.LENGTH;
            case "content_filter" -> FinishReason.CONTENT_FILTER;
            case "tool_calls" -> FinishReason.TOOL_CALLS;
            case null, default -> FinishReason.STOP;
        };
    }
}