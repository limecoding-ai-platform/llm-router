package com.limecoding.llmrouter.api.responses;

import com.limecoding.llmrouter.core.model.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;

/** The deliberately supported, stateless subset of the Responses wire contract. */
public class ResponsesMapper {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public ChatRequest toDomain(JsonNode request, String model) {
        fields(request, "request", "model", "input", "instructions", "tools", "tool_choice",
                "max_output_tokens", "temperature", "stream", "store");
        bool(request, "stream");
        if (Boolean.TRUE.equals(bool(request, "store"))) throw invalid("store=true is not supported");
        List<Message> messages = new ArrayList<>();
        String instructions = optionalText(request, "instructions");
        if (instructions != null) messages.add(new Message(Role.SYSTEM, instructions, null, null, null, null));
        JsonNode input = request.path("input");
        if (input.isString()) messages.add(Message.user(input.asText()));
        else if (input.isArray() && !input.isEmpty()) {
            for (JsonNode item : input) addInput(messages, item);
        } else throw invalid("input must be a string or non-empty array");
        validateCalls(messages);

        List<Tool> tools = new ArrayList<>();
        if (present(request, "tools")) {
            if (!request.path("tools").isArray()) throw invalid("tools must be an array");
            Set<String> names = new HashSet<>();
            for (JsonNode tool : request.path("tools")) {
                fields(tool, "tool", "type", "name", "description", "parameters", "strict");
                if (!"function".equals(text(tool, "type"))) throw invalid("Only function tools are supported");
                String name = text(tool, "name");
                if (!names.add(name)) throw invalid("Duplicate tool name");
                JsonNode parameters = tool.get("parameters");
                if (parameters == null || parameters.isNull()) parameters = JSON.createObjectNode().put("type", "object");
                if (!parameters.isObject()) throw invalid("parameters must be an object");
                tools.add(new Tool("function", new Tool.Function(name, optionalText(tool, "description"),
                        parameters, bool(tool, "strict"))));
            }
        }
        JsonNode choice = request.get("tool_choice");
        if (choice != null && !choice.isNull()) {
            if (choice.isString()) {
                if (!Set.of("auto", "none", "required").contains(choice.asText())) throw invalid("Invalid tool_choice");
                if ("required".equals(choice.asText()) && tools.isEmpty()) throw invalid("tool_choice requires tools");
            } else {
                fields(choice, "tool_choice", "type", "name");
                if (!"function".equals(text(choice, "type"))) throw invalid("Invalid tool_choice type");
                String name = text(choice, "name");
                if (tools.stream().noneMatch(t -> t.function().name().equals(name))) throw invalid("Unknown tool_choice name");
                var converted = JSON.createObjectNode().put("type", "function");
                converted.putObject("function").put("name", name);
                choice = converted;
            }
        }
        Integer max = null;
        if (present(request, "max_output_tokens")) {
            var value = request.path("max_output_tokens");
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() <= 0)
                throw invalid("max_output_tokens must be a positive integer");
            max = value.asInt();
        }
        Double temperature = null;
        if (present(request, "temperature")) {
            var value = request.path("temperature");
            if (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble() < 0 || value.asDouble() > 2)
                throw invalid("temperature must be between 0 and 2");
            temperature = value.asDouble();
        }
        return new ChatRequest(model, messages, tools, choice, max, temperature,
                Boolean.TRUE.equals(bool(request, "stream")));
    }

    private void addInput(List<Message> messages, JsonNode item) {
        if (!item.isObject()) throw invalid("input items must be objects");
        String type = present(item, "type") ? text(item, "type") : "message";
        switch (type) {
            case "message" -> {
                fields(item, "message", "type", "id", "status", "role", "content");
                String role = text(item, "role");
                Role mapped = switch (role) {
                    case "user" -> Role.USER;
                    case "assistant" -> Role.ASSISTANT;
                    case "system", "developer" -> Role.SYSTEM;
                    default -> throw invalid("Unsupported message role");
                };
                if (mapped == Role.SYSTEM && messages.stream().anyMatch(m -> m.role() != Role.SYSTEM))
                    throw invalid("system/developer messages must precede conversation messages");
                ParsedContent content = content(item.path("content"), mapped == Role.ASSISTANT);
                if (mapped == Role.ASSISTANT && !messages.isEmpty() && messages.getLast().role() == Role.ASSISTANT) {
                    Message previous = messages.removeLast();
                    messages.add(Message.assistant(join(previous.content(), content.text()), previous.toolCalls(),
                            join(previous.refusal(), content.refusal()), previous.annotations()));
                } else messages.add(new Message(mapped, content.text(), null, content.refusal(), null, null));
            }
            case "function_call" -> {
                fields(item, "function_call", "type", "id", "status", "call_id", "name", "arguments");
                String arguments = text(item, "arguments");
                try {
                    if (!JSON.reader().with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .readTree(arguments).isObject()) throw invalid("arguments must be a JSON object string");
                } catch (tools.jackson.core.JacksonException e) { throw invalid("Invalid function arguments JSON"); }
                var call = new Message.ToolCall(text(item, "call_id"), "function",
                        new Message.FunctionCall(text(item, "name"), arguments));
                if (!messages.isEmpty() && messages.getLast().role() == Role.ASSISTANT) {
                    Message previous = messages.removeLast();
                    var calls = new ArrayList<>(previous.toolCalls());
                    calls.add(call);
                    messages.add(Message.assistant(previous.content(), calls, previous.refusal(), previous.annotations()));
                } else messages.add(Message.assistant(null, List.of(call), null, null));
            }
            case "function_call_output" -> {
                fields(item, "function_call_output", "type", "id", "status", "call_id", "output");
                if (!item.path("output").isString()) throw invalid("function_call_output.output must be a string");
                messages.add(new Message(Role.TOOL, item.path("output").asText(), null, null, null, text(item, "call_id")));
            }
            default -> throw invalid("Unsupported input item type: " + type);
        }
    }

    private record ParsedContent(String text, String refusal) {}

    private static String join(String first, String second) {
        return first == null ? second : second == null ? first : first + second;
    }

    private ParsedContent content(JsonNode content, boolean assistant) {
        if (content.isString()) return new ParsedContent(content.asText(), null);
        if (!content.isArray() || content.isEmpty()) throw invalid("content must be a string or non-empty text array");
        String result = null, refusal = null;
        for (JsonNode part : content) {
            String type = text(part, "type");
            if (assistant && "refusal".equals(type)) {
                fields(part, "refusal", "type", "refusal");
                refusal = join(refusal, text(part, "refusal"));
                continue;
            }
            fields(part, "content", "type", "text", "annotations", "logprobs");
            if (!("input_text".equals(type) || assistant && "output_text".equals(type)))
                throw invalid("Only text content is supported");
            if (present(part, "annotations") && (!part.path("annotations").isArray() || !part.path("annotations").isEmpty()))
                throw invalid("Non-empty annotations are not supported as input");
            if (present(part, "logprobs") && (!part.path("logprobs").isArray() || !part.path("logprobs").isEmpty()))
                throw invalid("Non-empty logprobs are not supported as input");
            if (!part.path("text").isString()) throw invalid("text must be a string");
            result = join(result, part.path("text").asText());
        }
        return new ParsedContent(result, refusal);
    }

    private void validateCalls(List<Message> messages) {
        Set<String> seen = new HashSet<>(), pending = new HashSet<>();
        for (Message message : messages) {
            if (message.role() == Role.TOOL) {
                if (!pending.remove(message.toolCallId())) throw invalid("Unknown or duplicate function_call_output call_id");
            } else {
                if (!pending.isEmpty()) throw invalid("Every function call requires an output before the next message");
                for (var call : message.toolCalls()) {
                    if (!seen.add(call.id())) throw invalid("Duplicate function call_id");
                    pending.add(call.id());
                }
            }
        }
        if (!pending.isEmpty()) throw invalid("Every function call requires an output");
        if (messages.stream().allMatch(m -> m.role() == Role.SYSTEM)) throw invalid("input requires a conversation message");
    }

    public ResponsesResponse response(ChatResponse chat, String model) {
        List<Map<String, Object>> output = new ArrayList<>();
        Message message = chat.message();
        String status = status(chat.finishReason());
        List<Map<String, Object>> content = new ArrayList<>();
        if (message.content() != null) content.add(textPart(message.content()));
        if (message.refusal() != null) content.add(Map.of("type", "refusal", "refusal", message.refusal()));
        if (!content.isEmpty()) output.add(message(newId("msg_"), status, content));
        for (var call : message.toolCalls()) output.add(function(newId("fc_"), status, call.id(), call.function().name(), call.function().arguments()));
        return envelope(newId("resp_"), Instant.now().getEpochSecond(), model, status, output,
                usage(chat.usage(), null, null), null, incomplete(chat.finishReason()));
    }

    static ResponsesResponse envelope(String id, long created, String model, String status,
            List<Map<String, Object>> output, Map<String, Object> usage, Object error, Map<String, String> details) {
        return new ResponsesResponse(id, "response", created, status, model, List.copyOf(output), usage, error, details, false);
    }
    static Map<String, Object> textPart(String text) {
        return Map.of("type", "output_text", "text", text, "annotations", List.of(), "logprobs", List.of());
    }
    static Map<String, Object> message(String id, String status, List<Map<String, Object>> content) {
        return Map.of("id", id, "type", "message", "role", "assistant", "status", status, "content", List.copyOf(content));
    }
    static Map<String, Object> function(String id, String status, String callId, String name, String arguments) {
        return Map.of("id", id, "type", "function_call", "status", status, "call_id", callId, "name", name, "arguments", arguments);
    }
    static Map<String, Object> usage(Usage usage, Integer cached, Integer reasoning) {
        if (usage == null) return null;
        return Map.of("input_tokens", usage.promptTokens(), "output_tokens", usage.completionTokens(),
                "total_tokens", usage.totalTokens(), "input_tokens_details", Map.of("cached_tokens", cached == null ? 0 : cached),
                "output_tokens_details", Map.of("reasoning_tokens", reasoning == null ? 0 : reasoning));
    }
    static String status(FinishReason reason) {
        return reason == FinishReason.LENGTH || reason == FinishReason.CONTENT_FILTER ? "incomplete" : "completed";
    }
    static Map<String, String> incomplete(FinishReason reason) {
        return switch (reason) {
            case LENGTH -> Map.of("reason", "max_output_tokens");
            case CONTENT_FILTER -> Map.of("reason", "content_filter");
            default -> null;
        };
    }
    static String newId(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", ""); }
    public static String text(JsonNode node, String field) {
        if (!node.path(field).isString() || node.path(field).asText().isBlank()) throw invalid(field + " must be a non-blank string");
        return node.path(field).asText();
    }
    private static String optionalText(JsonNode node, String field) {
        if (!present(node, field)) return null;
        if (!node.path(field).isString()) throw invalid(field + " must be a string");
        return node.path(field).asText();
    }
    private static Boolean bool(JsonNode node, String field) {
        if (!present(node, field)) return null;
        if (!node.path(field).isBoolean()) throw invalid(field + " must be boolean");
        return node.path(field).asBoolean();
    }
    private static boolean present(JsonNode node, String field) { return node.hasNonNull(field); }
    private static void fields(JsonNode node, String label, String... allowed) {
        if (node == null || !node.isObject()) throw invalid(label + " must be an object");
        var names = Set.of(allowed);
        for (String name : node.propertyNames()) if (!names.contains(name)) throw invalid("Unsupported " + label + " field: " + name);
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
