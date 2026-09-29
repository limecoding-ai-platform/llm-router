package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.core.model.*;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiMessage;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiRequest;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiResponse;

import java.util.Locale;
import java.util.Map;


public class OpenAiMapper {

    private final Map<String, MaxTokensParam> dialects;

    public OpenAiMapper(Map<String, MaxTokensParam> dialects) {
        this.dialects = Map.copyOf(dialects);
    }


    public OpenAiRequest toWire(ChatRequest chatRequest) {
        MaxTokensParam dialect = dialects.get(chatRequest.model());

        if (dialect == null) {
            throw new IllegalArgumentException(
                    "토큰 파라미터 설정이 없는 모델입니다: " + chatRequest.model());
        }

        var messages = chatRequest.messages().stream()
                .map(this::toWire)
                .toList();

        return switch (dialect) {
            case MAX_TOKENS -> new OpenAiRequest(
                    chatRequest.model(),
                    messages,
                    chatRequest.maxTokens(),
                    null,
                    false
            );
            case MAX_COMPLETION_TOKENS -> new OpenAiRequest(
                    chatRequest.model(),
                    messages,
                    null,
                    chatRequest.maxTokens(),
                    false
            );
        };
    }

    public OpenAiRequest toWire(ChatRequest chatRequest, boolean stream) {
        var dialect = dialects.get(chatRequest.model());

        var messages = chatRequest.messages().stream()
                .map(this::toWire)
                .toList();

        return switch (dialect) {
            case MAX_TOKENS -> new OpenAiRequest(
                    chatRequest.model(),
                    messages,
                    chatRequest.maxTokens(),
                    null,
                    stream
            );
            case MAX_COMPLETION_TOKENS -> new OpenAiRequest(
                    chatRequest.model(),
                    messages,
                    null,
                    chatRequest.maxTokens(),
                    stream
            );
        };
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