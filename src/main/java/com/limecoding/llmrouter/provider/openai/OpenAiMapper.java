package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.core.model.*;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiMessage;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiRequest;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiResponse;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiTool;

import java.util.List;
import java.util.Locale;
import java.util.Map;


public class OpenAiMapper {

    private final Map<String, MaxTokensParam> dialects;

    public OpenAiMapper(Map<String, MaxTokensParam> dialects) {
        this.dialects = Map.copyOf(dialects);
    }


    public OpenAiRequest toWire(ChatRequest chatRequest) {
        return toWire(chatRequest, false);
    }

    public OpenAiRequest toWire(ChatRequest chatRequest, boolean stream) {
        MaxTokensParam dialect = dialects.get(chatRequest.model());

        if (dialect == null) {
            throw new IllegalArgumentException(
                    "토큰 파라미터 설정이 없는 모델입니다: " + chatRequest.model());
        }

        var messages = chatRequest.messages().stream()
                .map(this::toWire)
                .toList();
        List<OpenAiTool> openAiTool = chatRequest.tools().stream().map(tool -> {

            OpenAiTool.OpenAiFunction function = new OpenAiTool.OpenAiFunction(
                    tool.function().name(),
                    tool.function().description(),
                    tool.function().parameters(),
                    tool.function().strict()
            );

            return new OpenAiTool(
                    tool.type(),
                    function
            );
        }).toList();

        return switch (dialect) {
            case MAX_TOKENS -> new OpenAiRequest(
                    chatRequest.model(),
                    messages,
                    openAiTool,
                    chatRequest.toolChoice(),
                    chatRequest.maxTokens(),
                    null,
                    stream,
                    stream && chatRequest.includeUsage() ? new OpenAiRequest.StreamOptions(true) : null
            );
            case MAX_COMPLETION_TOKENS -> new OpenAiRequest(
                    chatRequest.model(),
                    messages,
                    openAiTool,
                    chatRequest.toolChoice(),
                    null,
                    chatRequest.maxTokens(),
                    stream,
                    stream && chatRequest.includeUsage() ? new OpenAiRequest.StreamOptions(true) : null
            );
        };
    }

    public ChatResponse toDomain(OpenAiResponse wire) {
        var choice = wire.choices().getFirst();
        var openAiToolCalls = choice.message().toolCalls();

        List<Message.ToolCall> toolCalls = openAiToolCalls.stream().map(toolcall -> {
            OpenAiMessage.FunctionCall function = toolcall.function();

            Message.FunctionCall functionCall = new Message.FunctionCall(function.name(), function.arguments());

            return new Message.ToolCall(
                    toolcall.id(),
                    toolcall.type(),
                    functionCall
            );
        }).toList();

        return new ChatResponse(
                wire.id(),
                wire.model(),
                Message.assistant(
                        choice.message().content(),
                        toolCalls,
                        choice.message().refusal(),
                        choice.message().annotations()
                ),
                toFinishReason(choice.finishReason()),
                toUsage(wire.usage())
        );
    }

    private Usage toUsage(OpenAiResponse.Usage wire) {
        return new Usage(wire.promptTokens(), wire.completionTokens());
    }

    private OpenAiMessage toWire(Message message) {
        var toolCalls = message.toolCalls();

        List<OpenAiMessage.ToolCall> openAiToolCall = toolCalls.stream().map(toolcall -> {
            Message.FunctionCall function = toolcall.function();

            OpenAiMessage.FunctionCall functionCall = new OpenAiMessage.FunctionCall(
                    function.name(),
                    function.arguments());

            return new OpenAiMessage.ToolCall(
                    toolcall.id(),
                    toolcall.type(),
                    functionCall
            );
        }).toList();

        return new OpenAiMessage(
                message.role().name().toLowerCase(Locale.ROOT),
                message.content(),
                openAiToolCall,
                message.refusal(),
                message.annotations()
        );
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
