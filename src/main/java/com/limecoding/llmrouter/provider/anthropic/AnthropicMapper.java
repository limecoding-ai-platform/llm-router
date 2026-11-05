package com.limecoding.llmrouter.provider.anthropic;

import com.limecoding.llmrouter.core.model.*;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicMessage;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicRequest;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicResponse;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicTool;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicContentBlock;
import com.limecoding.llmrouter.provider.anthropic.wire.toolchoice.AnthropicToolChoice;
import com.limecoding.llmrouter.provider.anthropic.wire.toolchoice.ToolChoiceType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.core.JacksonException;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

public class AnthropicMapper {

    // Anthropic은 max_tokens가 필수라 미지정 시 기본값을 채워준다.
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private final ObjectReader argumentsReader;

    public AnthropicMapper(ObjectMapper objectMapper) {
        this.argumentsReader = objectMapper.readerFor(JsonNode.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

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
        // 텍스트와 도구 호출은 섞여 올 수 있으므로 모든 블록을 순서대로 확인한다.
        List<String> texts = new ArrayList<>();
        List<Message.ToolCall> toolCalls = new ArrayList<>();
        for (AnthropicContentBlock block : wire.content()) {
            switch (block) {
                case AnthropicContentBlock.Text text -> texts.add(text.text());
                case AnthropicContentBlock.ToolUse tool -> toolCalls.add(new Message.ToolCall(
                        tool.id(),
                        "function",
                        // input은 JSON 객체지만 공통 모델의 arguments는 JSON 문자열이다.
                        new Message.FunctionCall(tool.name(), tool.input().toString())
                ));
                case AnthropicContentBlock.ToolResult ignored ->
                        throw new IllegalArgumentException("tool_result is not an assistant response block");
            }
        }

        return new ChatResponse(
                wire.id(),
                wire.model(),
                Message.assistant(texts.isEmpty() ? null : String.join("", texts), toolCalls, null, null),
                toFinishReason(wire.stopReason()),
                toUsage(wire.usage())
        );
    }

    private List<AnthropicMessage> convertChatMessagesToAnthropicMessage(ChatRequest chatRequest) {
        List<AnthropicMessage> messages = new ArrayList<>();
        List<AnthropicContentBlock> results = new ArrayList<>();
        for (Message message : chatRequest.messages()) {
            if (message.role() == Role.TOOL) {
                if (message.toolCallId() == null || message.toolCallId().isBlank()) {
                    throw new IllegalArgumentException("tool message requires a non-blank tool_call_id");
                }
                results.add(new AnthropicContentBlock.ToolResult(
                        message.toolCallId(), message.content()));
                continue;
            }

            // 병렬 도구 실행 결과는 하나의 user 메시지에 담고, 다음 대화 전에 확정한다.
            if (!results.isEmpty()) {
                messages.add(new AnthropicMessage("user", results));
                results.clear();
            }
            messages.add(toAnthropicMessage(message));
        }
        if (!results.isEmpty()) {
            messages.add(new AnthropicMessage("user", results));
        }
        return messages;
    }

    private Integer toMaxTokens(Integer maxTokens) {
        return maxTokens == null ? DEFAULT_MAX_TOKENS : maxTokens;
    }

    private AnthropicMessage toAnthropicMessage(Message message) {
        List<AnthropicContentBlock> blocks = new ArrayList<>();
        if (message.content() != null && !message.content().isEmpty()) {
            blocks.add(new AnthropicContentBlock.Text(message.content()));
        }
        if (message.role() == Role.ASSISTANT) {
            for (Message.ToolCall call : message.toolCalls()) {
                blocks.add(new AnthropicContentBlock.ToolUse(
                        call.id(), call.function().name(), toToolInput(call.function().arguments())));
            }
        }
        return new AnthropicMessage(
                message.role().name().toLowerCase(Locale.ROOT),
                blocks);
    }

    /**
     * 공통 모델의 arguments JSON 문자열을 Anthropic의 input 객체로 변환한다.
     * 예: 문자열 안의 {@code {"city":"Seoul"}}을 파싱하여 같은 구조의 JSON 객체로 전달한다.
     */
    private JsonNode toToolInput(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            throw new IllegalArgumentException("tool call arguments must be a JSON object");
        }
        final JsonNode input;
        try {
            input = argumentsReader.readValue(arguments);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("tool call arguments must be a valid JSON object", exception);
        }
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException("tool call arguments must be a JSON object");
        }
        return input;
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
