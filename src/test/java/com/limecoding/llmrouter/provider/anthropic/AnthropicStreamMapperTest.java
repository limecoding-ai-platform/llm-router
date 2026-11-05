package com.limecoding.llmrouter.provider.anthropic;

import com.limecoding.llmrouter.core.model.FinishReason;
import com.limecoding.llmrouter.core.model.StreamEvent;
import com.limecoding.llmrouter.core.model.Usage;
import com.limecoding.llmrouter.provider.anthropic.wire.chunk.AnthropicEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static com.limecoding.llmrouter.core.model.StreamEvent.*;
import static org.junit.jupiter.api.Assertions.*;

class AnthropicStreamMapperTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final AnthropicStreamMapper mapper = new AnthropicStreamMapper();

    @Test
    void mapsToolsAfterTextWithConsecutiveToolIndicesAndUnparsedArguments() {
        assertTrue(event("""
                {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}
                """).isEmpty());
        assertEquals(List.of(new TextDelta("확인할게요")), event("""
                {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"확인할게요"}}
                """));
        assertEquals(List.of(call(0, "tool-a", "function", "weather", "")), startTool(1, "tool-a", "weather"));
        assertEquals(List.of(call(0, null, null, null, "{\"city\":\"서")), arguments(1, "{\"city\":\"서"));
        assertEquals(List.of(call(0, null, null, null, "울\"}")), arguments(1, "울\"}"));
        assertTrue(event("{\"type\":\"content_block_stop\",\"index\":1}").isEmpty());
        assertEquals(List.of(call(1, "tool-b", "function", "time", "")), startTool(3, "tool-b", "time"));
        assertEquals(List.of(call(1, null, null, null, "{}")), arguments(3, "{}"));
    }

    @Test
    void preservesFinishReasonAndUsage() {
        assertTrue(event("""
                {"type":"message_start","message":{"id":"msg","model":"test","type":"message",
                "role":"assistant","content":[],"usage":{"input_tokens":12,"output_tokens":1}}}
                """).isEmpty());
        assertEquals(List.of(call(0, "tool-a", "function", "weather", "")), startTool(0, "tool-a", "weather"));
        assertEquals(List.of(new Finished(FinishReason.TOOL_CALLS), new UsageReported(new Usage(12, 20))), event("""
                {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":20}}
                """));
        assertTrue(event("{\"type\":\"message_stop\"}").isEmpty());
        assertTrue(event("{\"type\":\"ping\"}").isEmpty());
    }

    @Test
    void rejectsArgumentsWithoutAnActiveToolBlock() {
        assertThrows(IllegalArgumentException.class, () -> arguments(2, "{}"));
        startTool(2, "tool-a", "weather");
        event("{\"type\":\"content_block_stop\",\"index\":2}");
        assertThrows(IllegalArgumentException.class, () -> arguments(2, "{}"));
    }

    private List<StreamEvent> startTool(int index, String id, String name) {
        return event(json.writeValueAsString(Map.of("type", "content_block_start", "index", index,
                "content_block", Map.of("type", "tool_use", "id", id, "name", name, "input", Map.of()))));
    }

    private List<StreamEvent> arguments(int index, String fragment) {
        return event(json.writeValueAsString(Map.of("type", "content_block_delta", "index", index,
                "delta", Map.of("type", "input_json_delta", "partial_json", fragment))));
    }

    private ToolCallDelta call(int index, String id, String type, String name, String arguments) {
        return new ToolCallDelta(0, List.of(new ToolCall(index, id, type, new FunctionDelta(name, arguments))));
    }

    private List<StreamEvent> event(String payload) {
        return mapper.toEvents(json.readValue(payload, AnthropicEvent.class));
    }
}
