package com.limecoding.llmrouter.provider.openai.responses;

import com.limecoding.llmrouter.core.model.*;
import tools.jackson.databind.JsonNode;
import java.util.*;

public final class OpenAiResponsesMapper {
    public OpenAiResponsesRequest toWire(ChatRequest request, boolean stream) {
        List<Map<String, Object>> input = new ArrayList<>();
        for (Message message : request.messages()) {
            if (message.role() == Role.TOOL) {
                input.add(Map.of("type", "function_call_output", "call_id", message.toolCallId(), "output", message.content()));
                continue;
            }
            if (message.role() == Role.ASSISTANT) {
                // The live API rejects assistant input_text parts despite the SDK's broad input union.
                // Plain-string assistant history needs no stored output id/status and preserves its text.
                String content = message.content();
                if (message.refusal() != null) content = content == null || content.isEmpty()
                        ? message.refusal() : content + "\n\n" + message.refusal();
                if (content != null) input.add(Map.of("type", "message", "role", "assistant", "content", content));
                for (var call : message.toolCalls()) input.add(Map.of("type", "function_call", "call_id", call.id(),
                        "name", call.function().name(), "arguments", call.function().arguments()));
            } else input.add(Map.of("role", message.role().name().toLowerCase(Locale.ROOT), "content", message.content()));
        }
        List<Map<String, Object>> tools = new ArrayList<>();
        for (var tool : request.tools()) {
            var function = tool.function();
            Map<String, Object> wire = new LinkedHashMap<>();
            wire.put("type", "function");
            wire.put("name", function.name());
            if (function.description() != null) wire.put("description", function.description());
            if (function.parameters() != null) wire.put("parameters", function.parameters());
            // Responses defaults omitted strict to true; our common optional flag retains non-strict semantics.
            wire.put("strict", Boolean.TRUE.equals(function.strict()));
            tools.add(wire);
        }
        Object choice = null;
        if (request.toolChoice() != null && !request.toolChoice().isNull()) {
            choice = request.toolChoice().isString() ? request.toolChoice().asText()
                    : Map.of("type", "function", "name", request.toolChoice().path("function").path("name").asText());
        }
        return new OpenAiResponsesRequest(request.model(), input, tools, choice, request.maxTokens(), request.temperature(), stream, false);
    }

    public ChatResponse toDomain(JsonNode response) {
        if (response == null || !response.isObject()) throw invalid("Missing response body");
        String status = requiredText(response, "status");
        if (!(status.equals("completed") || status.equals("incomplete")) || response.hasNonNull("error"))
            throw invalid("Response did not complete successfully");
        if (!response.path("output").isArray()) throw invalid("Missing output array");
        StringBuilder text = new StringBuilder(), refusal = new StringBuilder();
        boolean hasText = false, hasRefusal = false;
        List<Message.ToolCall> calls = new ArrayList<>();
        List<JsonNode> annotations = new ArrayList<>();
        for (var item : response.path("output")) {
            switch (requiredText(item, "type")) {
                case "message" -> {
                    if (!item.path("content").isArray()) throw invalid("Missing message content");
                    for (var part : item.path("content")) {
                        switch (requiredText(part, "type")) {
                            case "output_text" -> {
                                text.append(requiredString(part, "text")); hasText = true;
                                if (part.path("annotations").isArray()) part.path("annotations").forEach(annotations::add);
                            }
                            case "refusal" -> { refusal.append(requiredString(part, "refusal")); hasRefusal = true; }
                            default -> throw invalid("Unsupported response content");
                        }
                    }
                }
                case "function_call" -> calls.add(new Message.ToolCall(requiredText(item, "call_id"), "function",
                        new Message.FunctionCall(requiredText(item, "name"), requiredString(item, "arguments"))));
                case "reasoning" -> { /* Private reasoning is not exposed by the common text/function contract. */ }
                default -> throw invalid("Unsupported response output item");
            }
        }
        FinishReason finish = calls.isEmpty() ? FinishReason.STOP : FinishReason.TOOL_CALLS;
        if (status.equals("incomplete")) finish = switch (requiredText(response.path("incomplete_details"), "reason")) {
            case "max_output_tokens" -> FinishReason.LENGTH;
            case "content_filter" -> FinishReason.CONTENT_FILTER;
            default -> throw invalid("Unknown incomplete reason");
        };
        JsonNode usage = response.path("usage");
        return new ChatResponse(requiredText(response, "id"), requiredText(response, "model"),
                Message.assistant(hasText ? text.toString() : null, calls, hasRefusal ? refusal.toString() : null, annotations),
                finish, new Usage(tokenCount(usage, "input_tokens"), tokenCount(usage, "output_tokens")));
    }

    public static int tokenCount(JsonNode node, String field) {
        var count = node.path(field);
        if (!count.isIntegralNumber() || !count.canConvertToInt() || count.intValue() < 0) throw invalid("Invalid token usage");
        return count.intValue();
    }
    public static String requiredString(JsonNode node, String field) {
        if (!node.path(field).isString()) throw invalid("Invalid upstream field: " + field);
        return node.path(field).asText();
    }
    public static String requiredText(JsonNode node, String field) {
        String value = requiredString(node, field);
        if (value.isBlank()) throw invalid("Empty upstream field: " + field);
        return value;
    }
    private static UpstreamProtocolException invalid(String message) { return new UpstreamProtocolException(message); }
}
