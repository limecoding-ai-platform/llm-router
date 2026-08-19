package com.limecoding.llmrouter.api;

import com.limecoding.llmrouter.api.dto.OpenAiChatRequest;
import com.limecoding.llmrouter.api.dto.OpenAiChatResponse;
import com.limecoding.llmrouter.core.ProviderAdapter;
import com.limecoding.llmrouter.core.model.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@RequestMapping("/v1")
@RestController
public class ChatCompletionsController {

    private final ProviderAdapter adapter;

    public ChatCompletionsController(ProviderAdapter adapter) {
        this.adapter = adapter;
    }

    @PostMapping("/chat/completions")
    public OpenAiChatResponse chat(@RequestBody OpenAiChatRequest request) {
        ChatRequest domain = toDomain(request);
        ChatResponse chat = adapter.chat(domain);

        return toApi(chat);
    }

    private ChatRequest toDomain(OpenAiChatRequest request) {
        List<Message> messages = request.messages().stream().map(this::toDomain).toList();
        return new ChatRequest(
                request.model(),
                messages,
                request.maxTokens(),
                request.temperature()
        );
    }

    private OpenAiChatResponse toApi(ChatResponse chat) {
        Message message = chat.message();
        OpenAiChatResponse.Choice choice = new OpenAiChatResponse.Choice(
                0,
                new OpenAiChatResponse.OpenAIChatMessage(
                        message.role().name().toLowerCase(Locale.ROOT),
                        message.content()),
                toWireFinishReason(chat.finishReason())
        );

        OpenAiChatResponse.Usage usage = new OpenAiChatResponse.Usage(
                chat.usage().promptTokens(),
                chat.usage().completionTokens(),
                chat.usage().totalToken()
        );

        return new OpenAiChatResponse(
                chat.id(),
                "chat.completion",
                Instant.now().getEpochSecond(),
                chat.model(),
                List.of(choice),
                usage
        );
    }

    private Message toDomain(OpenAiChatRequest.OpenAiMessage message) {
        return new Message(
                Role.valueOf(message.role().toUpperCase(Locale.ROOT)),
                message.content()
        );
    }

    private String toWireFinishReason(FinishReason reason) {
        return switch (reason) {
            case STOP -> "stop";
            case LENGTH -> "length";
            case CONTENT_FILTER -> "content_filter";
            case TOOL_CALLS -> "tool_calls";
        };
    }
}
