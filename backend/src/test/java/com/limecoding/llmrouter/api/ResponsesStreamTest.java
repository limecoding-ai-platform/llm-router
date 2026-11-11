package com.limecoding.llmrouter.api;

import com.limecoding.llmrouter.api.responses.ResponsesStream;
import com.limecoding.llmrouter.api.responses.ResponsesMapper;
import com.limecoding.llmrouter.core.model.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResponsesStreamTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void accumulatesInterleavedTextToolsAndLateUsageWithStableIds() {
        List<Map<String, Object>> events = new ArrayList<>();
        var stream = new ResponsesStream("openai/test", events::add);
        stream.accept(new StreamEvent.TextDelta("Hel"));
        stream.accept(tool(0, "c1", "weather", "{"));
        stream.accept(tool(1, "c2", "weather", "{}"));
        stream.accept(new StreamEvent.TextDelta("lo"));
        stream.accept(tool(0, null, null, "}"));
        stream.accept(new StreamEvent.Finished(FinishReason.TOOL_CALLS));
        assertFalse(events.stream().anyMatch(e -> e.get("type").equals("response.completed")));
        stream.accept(new StreamEvent.UsageReported(new Usage(10, 5), 2, 3));
        stream.complete();
        for (int i = 0; i < events.size(); i++) assertEquals(i, events.get(i).get("sequence_number"));
        assertEquals("response.created", events.getFirst().get("type"));
        var last = json.valueToTree(events.getLast());
        assertEquals("response.completed", last.path("type").asText());
        assertEquals("Hello", last.at("/response/output/0/content/0/text").asText());
        assertEquals("{}", last.at("/response/output/1/arguments").asText());
        assertEquals("c2", last.at("/response/output/2/call_id").asText());
        assertEquals(2, last.at("/response/usage/input_tokens_details/cached_tokens").asInt());
        assertEquals(3, last.at("/response/usage/output_tokens_details/reasoning_tokens").asInt());
        var added = events.stream().filter(e -> e.get("type").equals("response.output_item.added")).toList();
        for (var event : added) {
            var tree = json.valueToTree(event);
            int index = tree.path("output_index").asInt();
            assertEquals(tree.at("/item/id"), last.at("/response/output/" + index + "/id"));
        }
    }

    @Test void handlesRefusalAndIncompleteTermination() {
        List<Map<String, Object>> events = new ArrayList<>();
        var stream = new ResponsesStream("test", events::add);
        stream.accept(new StreamEvent.RefusalDelta(0, "Cannot help"));
        stream.accept(new StreamEvent.Finished(FinishReason.CONTENT_FILTER));
        stream.complete();
        var last = json.valueToTree(events.getLast());
        assertEquals("response.incomplete", last.path("type").asText());
        assertEquals("Cannot help", last.at("/response/output/0/content/0/refusal").asText());
        assertEquals("content_filter", last.at("/response/incomplete_details/reason").asText());
    }

    @Test void failsTruncatedStreamAndDoesNotCompleteTwice() {
        List<Map<String, Object>> events = new ArrayList<>();
        var stream = new ResponsesStream("test", events::add);
        stream.accept(new StreamEvent.TextDelta("partial"));
        stream.complete();
        assertEquals("response.failed", events.getLast().get("type"));
        int size = events.size();
        stream.fail();
        stream.complete();
        assertEquals(size, events.size());
        assertFalse(events.stream().anyMatch(e -> e.get("type").equals("response.completed")));
    }

    @Test void emitsLengthIncompleteAndRejectsMissingToolIdentity() {
        List<Map<String, Object>> events = new ArrayList<>();
        var stream = new ResponsesStream("test", events::add);
        stream.accept(tool(0, null, null, "{}"));
        stream.accept(new StreamEvent.Finished(FinishReason.TOOL_CALLS));
        stream.complete();
        assertEquals("response.failed", events.getLast().get("type"));
        events.clear();
        stream = new ResponsesStream("test", events::add);
        stream.accept(new StreamEvent.TextDelta("partial"));
        stream.accept(new StreamEvent.Finished(FinishReason.LENGTH));
        stream.complete();
        assertEquals("response.incomplete", events.getLast().get("type"));
    }

    private StreamEvent.ToolCallDelta tool(int index, String id, String name, String args) {
        return new StreamEvent.ToolCallDelta(0, List.of(new StreamEvent.ToolCall(index, id, "function",
                new StreamEvent.FunctionDelta(name, args))));
    }

    @Test void toolFirstOutputCanBeReplayedWithItsResult() {
        List<Map<String, Object>> events = new ArrayList<>();
        var stream = new ResponsesStream("test", events::add);
        stream.accept(tool(0, "c1", "weather", "{}"));
        stream.accept(new StreamEvent.TextDelta("Checking weather"));
        stream.accept(new StreamEvent.Finished(FinishReason.TOOL_CALLS));
        stream.complete();
        var body = json.createObjectNode();
        var input = body.putArray("input");
        input.addObject().put("role", "user").put("content", "weather?");
        for (var item : json.valueToTree(events.getLast()).at("/response/output")) input.add(item);
        input.addObject().put("type", "function_call_output").put("call_id", "c1").put("output", "sunny");
        var domain = new ResponsesMapper().toDomain(body, "test");
        assertEquals(3, domain.messages().size());
        assertEquals("Checking weather", domain.messages().get(1).content());
        assertEquals("c1", domain.messages().get(1).toolCalls().getFirst().id());
        assertEquals("c1", domain.messages().getLast().toolCallId());
    }
}
