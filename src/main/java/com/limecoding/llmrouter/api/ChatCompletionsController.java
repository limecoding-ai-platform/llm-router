package com.limecoding.llmrouter.api;

import com.limecoding.llmrouter.api.dto.chat.OpenAiChatRequest;
import com.limecoding.llmrouter.api.dto.chat.OpenAiChatResponse;
import com.limecoding.llmrouter.api.mapper.ChatCompletionChunkResponse;
import com.limecoding.llmrouter.core.ProviderRegistry;
import com.limecoding.llmrouter.core.model.*;
import jakarta.validation.Valid;
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
    public ResponseEntity<?> chat(@Valid @RequestBody OpenAiChatRequest request) {
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
                    if (event instanceof StreamEvent.UsageReported && !domain.includeUsage()) return;
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
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private ChatRequest toDomain(OpenAiChatRequest request, String model) {
        List<Message> messages = request.messages().stream().map(this::toDomain).toList();

        List<Tool> tools = request.tools().stream().map(tool -> {

            Tool.Function function = new Tool.Function(
                    tool.function().name(),
                    tool.function().description(),
                    tool.function().parameters(),
                    tool.function().strict()
            );

            return new Tool(
                    tool.type(),
                    function
            );
        }).toList();

        return new ChatRequest(
                model,
                messages,
                tools,
                request.toolChoice(),
                request.maxTokens(),
                request.temperature(),
                Boolean.TRUE.equals(request.stream()) && request.streamOptions() != null
                        && Boolean.TRUE.equals(request.streamOptions().includeUsage())
        );
    }

    private OpenAiChatResponse toApi(ChatResponse chat) {
        Message message = chat.message();
        OpenAiChatResponse.Choice choice = new OpenAiChatResponse.Choice(
                0,
                new OpenAiChatResponse.OpenAIChatMessage(
                        message.role().name().toLowerCase(Locale.ROOT),
                        message.content(),
                        message.toolCalls().stream()
                                .map(tool -> new OpenAiChatResponse.ToolCall(
                                        tool.id(),
                                        tool.type(),
                                        new OpenAiChatResponse.FunctionCall(
                                                tool.function().name(), tool.function().arguments())))
                                .toList()),
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

        List<Message.ToolCall> toolCall = message.toolCalls().stream().map(tool -> {

            Message.FunctionCall functionCall = new Message.FunctionCall(
                    tool.function().name(),
                    tool.function().arguments()
            );

            return new Message.ToolCall(
                    tool.id(),
                    tool.type(),
                    functionCall
            );
        }).toList();

        return new Message(
                Role.valueOf(message.role().toUpperCase(Locale.ROOT)),
                message.content(),
                toolCall,
                message.refusal(),
                message.annotations()
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
            case StreamEvent.RoleDelta d -> new ChatCompletionChunkResponse(
                    id, "chat.completion.chunk", created, model,
                    List.of(new ChatCompletionChunkResponse.Choice(d.choiceIndex(),
                            new ChatCompletionChunkResponse.Delta(d.role(), null, null, null), null)), null);
            case StreamEvent.TextDelta d -> new ChatCompletionChunkResponse(
                    id, "chat.completion.chunk", created, model,
                    List.of(new ChatCompletionChunkResponse.Choice(d.choiceIndex(), new ChatCompletionChunkResponse.Delta(null, d.text(), null, null), null)),
                    null);
            case StreamEvent.RefusalDelta d -> new ChatCompletionChunkResponse(
                    id, "chat.completion.chunk", created, model,
                    List.of(new ChatCompletionChunkResponse.Choice(d.choiceIndex(),
                            new ChatCompletionChunkResponse.Delta(null, null, d.refusal(), null), null)), null);
            case StreamEvent.ToolCallDelta d -> new ChatCompletionChunkResponse(
                    id, "chat.completion.chunk", created, model,
                    List.of(new ChatCompletionChunkResponse.Choice(d.choiceIndex(),
                            new ChatCompletionChunkResponse.Delta(null, null, null,
                                    d.toolCalls().stream().map(call -> new ChatCompletionChunkResponse.ToolCall(
                                            call.index(), call.id(), call.type(),
                                            call.function() == null ? null : new ChatCompletionChunkResponse.FunctionDelta(
                                                    call.function().name(), call.function().arguments()))).toList()), null)),
                    null);

            case StreamEvent.Finished f -> new ChatCompletionChunkResponse(
                    id, "chat.completion.chunk", created, model,
                    List.of(new ChatCompletionChunkResponse.Choice(f.choiceIndex(), new ChatCompletionChunkResponse.Delta(null, null, null, null), toWireFinishReason(f.reason()))),
                    null);

            case StreamEvent.UsageReported u -> new ChatCompletionChunkResponse(
                    id, "chat.completion.chunk", created, model,
                    List.of(),
                    toWireUsage(u));
        };
    }

    private ChatCompletionChunkResponse.Usage toWireUsage(StreamEvent.UsageReported event) {
        var usage = event.usage();
        return new ChatCompletionChunkResponse.Usage(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                event.cachedTokens() == null ? null : new ChatCompletionChunkResponse.PromptTokensDetails(event.cachedTokens()),
                event.reasoningTokens() == null ? null : new ChatCompletionChunkResponse.CompletionTokensDetails(event.reasoningTokens())
        );
    }
}
