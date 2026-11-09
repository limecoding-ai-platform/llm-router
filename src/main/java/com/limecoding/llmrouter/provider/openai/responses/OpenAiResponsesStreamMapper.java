package com.limecoding.llmrouter.provider.openai.responses;

import com.limecoding.llmrouter.core.model.*;
import tools.jackson.databind.JsonNode;
import java.util.*;
import java.util.function.Consumer;
import static com.limecoding.llmrouter.provider.openai.responses.OpenAiResponsesMapper.*;

/** One instance per upstream stream. Output indexes include reasoning, so tool indexes are separate. */
public final class OpenAiResponsesStreamMapper {
    private final OpenAiResponsesMapper mapper = new OpenAiResponsesMapper();
    private final Map<Integer, Call> calls = new LinkedHashMap<>();
    private final Map<Part, StringBuilder> text = new HashMap<>();
    private boolean started;
    private boolean terminal;

    public boolean accept(JsonNode event, Consumer<StreamEvent> sink) {
        if (terminal) return true;
        String type = requiredText(event, "type");
        if (type.equals("error") || type.equals("response.failed")) throw invalid("OpenAI Responses stream failed");
        if (!started) { sink.accept(new StreamEvent.RoleDelta(0, "assistant")); started = true; }
        switch (type) {
            case "response.output_item.added" -> {
                JsonNode item = event.path("item");
                if ("function_call".equals(item.path("type").asText())) {
                    Call call = register(index(event, "output_index"), item, sink);
                    if (item.hasNonNull("arguments")) arguments(call, requiredString(item, "arguments"), true, sink);
                }
            }
            case "response.output_text.delta", "response.refusal.delta" ->
                    text(part(event, type.contains("refusal")), requiredString(event, "delta"), false, sink);
            case "response.output_text.done", "response.refusal.done" -> {
                boolean refusal = type.contains("refusal");
                text(part(event, refusal), requiredString(event, refusal ? "refusal" : "text"), true, sink);
            }
            case "response.function_call_arguments.delta", "response.function_call_arguments.done" -> {
                Call call = calls.get(index(event, "output_index"));
                if (call == null) throw invalid("Arguments received before function identity");
                boolean done = type.endsWith(".done");
                arguments(call, requiredString(event, done ? "arguments" : "delta"), done, sink);
            }
            case "response.output_item.done" -> reconcile(index(event, "output_index"), event.path("item"), sink);
            case "response.completed", "response.incomplete" -> {
                JsonNode response = event.path("response");
                if (!type.equals("response." + response.path("status").asText())) throw invalid("Mismatched terminal response status");
                ChatResponse domain = mapper.toDomain(response);
                int outputIndex = 0;
                for (JsonNode item : response.path("output")) reconcile(outputIndex++, item, sink);
                sink.accept(new StreamEvent.Finished(domain.finishReason()));
                JsonNode usage = response.path("usage");
                sink.accept(new StreamEvent.UsageReported(domain.usage(), detail(usage, "input_tokens_details", "cached_tokens"),
                        detail(usage, "output_tokens_details", "reasoning_tokens")));
                terminal = true;
            }
            default -> { /* Lifecycle, private reasoning, and content-part notifications carry no public delta. */ }
        }
        return terminal;
    }

    private void reconcile(int outputIndex, JsonNode item, Consumer<StreamEvent> sink) {
        switch (requiredText(item, "type")) {
            case "function_call" -> arguments(register(outputIndex, item, sink), requiredString(item, "arguments"), true, sink);
            case "message" -> {
                if (!item.path("content").isArray()) throw invalid("Missing message content");
                int contentIndex = 0;
                for (JsonNode content : item.path("content")) {
                    String type = requiredText(content, "type");
                    if (type.equals("output_text")) text(new Part(outputIndex, contentIndex, false), requiredString(content, "text"), true, sink);
                    else if (type.equals("refusal")) text(new Part(outputIndex, contentIndex, true), requiredString(content, "refusal"), true, sink);
                    else throw invalid("Unsupported streamed content");
                    contentIndex++;
                }
            }
            case "reasoning" -> { }
            default -> throw invalid("Unsupported streamed output item");
        }
    }

    private Call register(int outputIndex, JsonNode item, Consumer<StreamEvent> sink) {
        String callId = requiredText(item, "call_id"), name = requiredText(item, "name");
        Call call = calls.get(outputIndex);
        if (call != null) {
            if (!call.id.equals(callId) || !call.name.equals(name)) throw invalid("Function identity changed during streaming");
            return call;
        }
        if (calls.values().stream().anyMatch(c -> c.id.equals(callId))) throw invalid("Duplicate function call_id");
        call = new Call(calls.size(), callId, name);
        calls.put(outputIndex, call);
        sink.accept(new StreamEvent.ToolCallDelta(0, List.of(new StreamEvent.ToolCall(call.index, call.id, "function",
                new StreamEvent.FunctionDelta(call.name, "")))));
        return call;
    }

    private void arguments(Call call, String value, boolean complete, Consumer<StreamEvent> sink) {
        String delta = append(call.arguments, value, complete);
        if (!delta.isEmpty()) sink.accept(new StreamEvent.ToolCallDelta(0, List.of(new StreamEvent.ToolCall(call.index, null, null,
                new StreamEvent.FunctionDelta(null, delta)))));
    }

    private void text(Part key, String value, boolean complete, Consumer<StreamEvent> sink) {
        String delta = append(text.computeIfAbsent(key, ignored -> new StringBuilder()), value, complete);
        if (!delta.isEmpty()) sink.accept(key.refusal ? new StreamEvent.RefusalDelta(0, delta) : new StreamEvent.TextDelta(delta));
    }

    private String append(StringBuilder current, String value, boolean complete) {
        if (complete) {
            if (!value.startsWith(current.toString())) throw invalid("Completed content differs from streamed content");
            value = value.substring(current.length());
        }
        current.append(value);
        return value;
    }
    private Part part(JsonNode event, boolean refusal) { return new Part(index(event, "output_index"), index(event, "content_index"), refusal); }
    private int index(JsonNode event, String field) { return tokenCount(event, field); }
    private Integer detail(JsonNode usage, String group, String field) {
        return usage.path(group).hasNonNull(field) ? tokenCount(usage.path(group), field) : null;
    }
    private static UpstreamProtocolException invalid(String message) { return new UpstreamProtocolException(message); }
    private record Part(int outputIndex, int contentIndex, boolean refusal) {}
    private static final class Call {
        final int index;
        final String id, name;
        final StringBuilder arguments = new StringBuilder();
        Call(int index, String id, String name) { this.index = index; this.id = id; this.name = name; }
    }
}
