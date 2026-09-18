package com.limecoding.llmrouter.api;

import com.limecoding.llmrouter.api.dto.chat.OpenAiChatRequest;
import com.limecoding.llmrouter.api.dto.chat.OpenAiChatResponse;
import com.limecoding.llmrouter.api.mapper.ChatCompletionChunkResponse;
import com.limecoding.llmrouter.config.ProviderRegistry;
import com.limecoding.llmrouter.core.model.*;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RequestMapping("/v1")
@RestController
public class ChatCompletionsController {
    private final ProviderRegistry registry;

    public ChatCompletionsController(ProviderRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/chat/completions")
    public ResponseEntity<?> chat(@RequestBody OpenAiChatRequest request) {
        if (Boolean.TRUE.equals(request.stream())) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .cacheControl(CacheControl.noCache())
                    .body(chatStream(request));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(chatCompletion(request));
    }

    private OpenAiChatResponse chatCompletion(OpenAiChatRequest request) {
        ModelRef parse = ModelRef.parse(request.model());
        ChatRequest domain = toDomain(request, parse.model());
        ChatResponse chat = registry.get(parse.provider()).chat(domain);

        return toApi(chat);
    }


    private SseEmitter chatStream(OpenAiChatRequest request) {
        var emitter = new SseEmitter(0L);
        String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
        long created = Instant.now().getEpochSecond();
        ModelRef ref = ModelRef.parse(request.model());
        var domain = toDomain(request, ref.model());
        var adapter = registry.get(ref.provider());

        Thread.startVirtualThread(() -> {
            try {
                adapter.stream(domain, event -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .data(toChunk(event, id, ref.original(), created),
                                        MediaType.APPLICATION_JSON
                                ));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
//            } catch (ClientGoneException e) {
//                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private ChatRequest toDomain(OpenAiChatRequest request, String model) {
        List<Message> messages = request.messages().stream().map(this::toDomain).toList();
        return new ChatRequest(
                model,
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
                chat.usage().totalTokens()
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

    private ChatCompletionChunkResponse toChunk(StreamEvent event, String id,
                                                String model, long created) {
        return switch (event) {
            case StreamEvent.TextDelta d -> new ChatCompletionChunkResponse(
                    id, "chat.completion.chunk", created, model,
                    List.of(new ChatCompletionChunkResponse.Choice(0, new ChatCompletionChunkResponse.Delta(null, d.text()), null)),
                    null);

            case StreamEvent.Finished f -> new ChatCompletionChunkResponse(
                    id, "chat.completion.chunk", created, model,
                    List.of(new ChatCompletionChunkResponse.Choice(0, new ChatCompletionChunkResponse.Delta(null, null), toWire(f.reason()))),
                    null);

            case StreamEvent.UsageReported u -> new ChatCompletionChunkResponse(
                    id, "chat.completion.chunk", created, model,
                    List.of(),                                    // ← 빈 배열
                    toWireUsage(u.usage()));
        };
    }

    private String toWire(FinishReason r) {
        return switch (r) {
            case STOP -> "stop";
            case LENGTH -> "length";
            case CONTENT_FILTER -> "content_filter";
            case TOOL_CALLS -> "tool_calls";
        };
    }

    private ChatCompletionChunkResponse.Usage toWireUsage(Usage usage) {
        return new ChatCompletionChunkResponse.Usage(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens()      // ← 도메인이 계산해준 값
        );
    }
}
