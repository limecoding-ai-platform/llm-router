package com.limecoding.llmrouter.provider.anthropic;

import com.limecoding.llmrouter.core.model.*;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicMessage;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicRequest;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicResponse;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicTool;
import com.limecoding.llmrouter.provider.anthropic.wire.toolchoice.AnthropicToolChoice;
import com.limecoding.llmrouter.provider.anthropic.wire.toolchoice.ToolChoiceType;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;

public class AnthropicMapper {

    // Anthropic은 max_tokens가 필수라 미지정 시 기본값을 채워준다.
    private static final int DEFAULT_MAX_TOKENS = 1024;

    public AnthropicRequest toWire(ChatRequest chatRequest) {
        return toWire(chatRequest, false);
    }

    public AnthropicRequest toWire(ChatRequest chatRequest, boolean stream) {
        List<Tool> tools = chatRequest.tools();
        List<AnthropicTool> toolList = tools.stream().map(this::toAnthropicTool).toList();

        return AnthropicRequest.builder()
                .model(chatRequest.model())
                .maxTokens(toMaxTokens(chatRequest.maxTokens()))
                .toolChoice(toAnthropicToolChoice(chatRequest.toolChoice()))
                .tools(toolList)
                .messages(convertChatMessagesToAnthropicMessage(chatRequest))
                .stream(stream)
                .build();
    }

    public ChatResponse toDomain(AnthropicResponse wire) {
        return new ChatResponse(
                wire.id(),
                wire.model(),
                Message.assistant(wire.content().getFirst().text(), null, null, null),
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

    /**
     * OpenAI 요청의 tool_choice를 Anthropic 형식으로 변환한다.
     * 문자열 "auto", "none", "required"는 각각 auto, none, any가 된다.
     * {@code {"type":"function","function":{"name":"get_weather"}}}는
     * {@code {"type":"tool","name":"get_weather"}}로 변환한다.
     * 생략 또는 JSON null은 선택 옵션을 전송하지 않는다.
     */
    private AnthropicToolChoice toAnthropicToolChoice(JsonNode toolChoice) {
        if (toolChoice == null || toolChoice.isNull()) {
            return null;
        }

        if (toolChoice.isString()) {
            ToolChoiceType type = switch (toolChoice.asString()) {
                case "auto" -> ToolChoiceType.AUTO;
                case "none" -> ToolChoiceType.NONE;
                case "required" -> ToolChoiceType.ANY;
                default -> throw new IllegalArgumentException("Unsupported tool_choice value");
            };
            return new AnthropicToolChoice(type, null);
        }

        if (toolChoice.isObject()) {
            JsonNode type = toolChoice.path("type");
            JsonNode name = toolChoice.path("function").path("name");
            if (type.isString() && "function".equals(type.asString())
                    && name.isString() && !name.asString().isBlank()) {
                return new AnthropicToolChoice(ToolChoiceType.TOOL, name.asString());
            }
        }

        throw new IllegalArgumentException(
                "tool_choice must be auto, none, required, or a function object with a non-blank name");
    }

    private AnthropicTool toAnthropicTool(Tool tool) {
        Tool.Function function = tool.function();

        return AnthropicTool.builder()
                .name(function.name())
                .description(function.description())
                .inputSchema(function.parameters().asObject())
                .build();
    }
}
