package com.limecoding.llmrouter.api.responses;

import com.limecoding.llmrouter.core.model.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Per-request state; only the provider worker thread may mutate it. */
public final class ResponsesStream {
    private final String id = ResponsesMapper.newId("resp_");
    private final long created = Instant.now().getEpochSecond();
    private final String model;
    private final Consumer<Map<String, Object>> sink;
    private final List<Item> items = new ArrayList<>();
    private final Map<Integer, FunctionItem> calls = new HashMap<>();
    private MessageItem message;
    private Map<String, Object> usage;
    private FinishReason finish;
    private int sequence;
    private boolean terminal;

    public ResponsesStream(String model, Consumer<Map<String, Object>> sink) {
        this.model = model;
        this.sink = sink;
        emit("response.created", Map.of("response", response("in_progress", null)));
        emit("response.in_progress", Map.of("response", response("in_progress", null)));
    }

    public void accept(StreamEvent event) {
        if (terminal) return;
        switch (event) {
            case StreamEvent.RoleDelta d -> checkChoice(d.choiceIndex());
            case StreamEvent.TextDelta d -> {
                checkChoice(d.choiceIndex());
                contentDelta(false, d.text());
            }
            case StreamEvent.RefusalDelta d -> {
                checkChoice(d.choiceIndex());
                contentDelta(true, d.refusal());
            }
            case StreamEvent.ToolCallDelta d -> {
                checkChoice(d.choiceIndex());
                for (var delta : d.toolCalls()) toolDelta(delta);
            }
            case StreamEvent.Finished f -> { checkChoice(f.choiceIndex()); finish = f.reason(); }
            case StreamEvent.UsageReported u -> usage = ResponsesMapper.usage(u.usage(), u.cachedTokens(), u.reasoningTokens());
        }
    }

    private void checkChoice(int index) {
        if (index != 0) throw new IllegalStateException("Responses supports one choice");
    }

    private void contentDelta(boolean refusal, String delta) {
        if (delta == null || delta.isEmpty()) return;
        if (message == null) {
            message = new MessageItem(items.size());
            items.add(message);
            emit("response.output_item.added", Map.of("output_index", message.index, "item", message.snapshot("in_progress")));
        }
        Part part = message.parts.stream().filter(p -> p.refusal == refusal).findFirst().orElse(null);
        if (part == null) {
            part = new Part(message.parts.size(), refusal);
            message.parts.add(part);
            emit("response.content_part.added", Map.of("item_id", message.id, "output_index", message.index,
                    "content_index", part.index, "part", part.snapshot()));
        }
        part.value.append(delta);
        emit(refusal ? "response.refusal.delta" : "response.output_text.delta",
                Map.of("item_id", message.id, "output_index", message.index, "content_index", part.index,
                        "delta", delta, "logprobs", List.of()));
    }

    private void toolDelta(StreamEvent.ToolCall delta) {
        if (delta.index() < 0) throw new IllegalStateException("Invalid tool index");
        FunctionItem item = calls.computeIfAbsent(delta.index(), ignored -> {
            var newItem = new FunctionItem(items.size());
            items.add(newItem);
            return newItem;
        });
        if (delta.id() != null) {
            if (item.callId != null && !item.callId.equals(delta.id())) throw new IllegalStateException("Changing call id");
            item.callId = delta.id();
        }
        if (delta.function() != null) {
            if (delta.function().name() != null) item.name.append(delta.function().name());
            if (delta.function().arguments() != null) item.arguments.append(delta.function().arguments());
        }
        // A malformed/fragmented upstream may send arguments before identity. Buffer until identifiable.
        if (!item.announced && item.callId != null && !item.callId.isBlank() && !item.name.isEmpty()) {
            item.announced = true;
            emit("response.output_item.added", Map.of("output_index", item.index,
                    "item", ResponsesMapper.function(item.id, "in_progress", item.callId, item.name.toString(), "")));
        }
        if (item.announced && item.emitted < item.arguments.length()) {
            String fragment = item.arguments.substring(item.emitted);
            item.emitted = item.arguments.length();
            emit("response.function_call_arguments.delta", Map.of("item_id", item.id, "output_index", item.index, "delta", fragment));
        }
    }

    public void complete() {
        if (terminal) return;
        if (finish == null || calls.values().stream().anyMatch(c -> !c.announced)) { fail(); return; }
        String status = ResponsesMapper.status(finish);
        for (Item item : items) {
            if (item instanceof MessageItem m) {
                for (Part part : m.parts) {
                    emit(part.refusal ? "response.refusal.done" : "response.output_text.done",
                            Map.of("item_id", m.id, "output_index", m.index, "content_index", part.index,
                                    part.refusal ? "refusal" : "text", part.value.toString(), "logprobs", List.of()));
                    emit("response.content_part.done", Map.of("item_id", m.id, "output_index", m.index,
                            "content_index", part.index, "part", part.snapshot()));
                }
            } else if (item instanceof FunctionItem f) {
                emit("response.function_call_arguments.done", Map.of("item_id", f.id, "output_index", f.index,
                        "name", f.name.toString(), "arguments", f.arguments.toString()));
            }
            emit("response.output_item.done", Map.of("output_index", item.index, "item", item.snapshot(status)));
        }
        emit("response." + status, Map.of("response", response(status, null)));
        terminal = true;
    }

    public void fail() {
        if (terminal) return;
        emit("response.failed", Map.of("response", response("failed",
                Map.of("code", "server_error", "message", "Upstream response stream failed"))));
        terminal = true;
    }

    private ResponsesResponse response(String status, Object error) {
        List<Map<String, Object>> output = items.stream()
                .filter(item -> !(item instanceof FunctionItem f) || f.announced)
                .map(item -> item.snapshot("failed".equals(status) ? "incomplete" : status)).toList();
        return ResponsesMapper.envelope(id, created, model, status, output, usage, error,
                "incomplete".equals(status) ? ResponsesMapper.incomplete(finish) : null);
    }

    private void emit(String type, Map<String, Object> data) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("sequence_number", sequence++);
        event.putAll(data);
        sink.accept(Collections.unmodifiableMap(event));
    }

    private abstract static class Item {
        final int index;
        final String id;
        Item(int index, String prefix) { this.index = index; this.id = ResponsesMapper.newId(prefix); }
        abstract Map<String, Object> snapshot(String status);
    }
    private static final class MessageItem extends Item {
        final List<Part> parts = new ArrayList<>();
        MessageItem(int index) { super(index, "msg_"); }
        @Override Map<String, Object> snapshot(String status) {
            return ResponsesMapper.message(id, status, parts.stream().map(Part::snapshot).toList());
        }
    }
    private static final class FunctionItem extends Item {
        String callId;
        final StringBuilder name = new StringBuilder(), arguments = new StringBuilder();
        boolean announced;
        int emitted;
        FunctionItem(int index) { super(index, "fc_"); }
        @Override Map<String, Object> snapshot(String status) {
            return ResponsesMapper.function(id, status, callId, name.toString(), arguments.toString());
        }
    }
    private static final class Part {
        final int index;
        final boolean refusal;
        final StringBuilder value = new StringBuilder();
        Part(int index, boolean refusal) { this.index = index; this.refusal = refusal; }
        Map<String, Object> snapshot() {
            return refusal ? Map.of("type", "refusal", "refusal", value.toString()) : ResponsesMapper.textPart(value.toString());
        }
    }
}
