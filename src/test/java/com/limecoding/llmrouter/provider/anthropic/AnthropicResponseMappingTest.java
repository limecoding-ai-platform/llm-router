package com.limecoding.llmrouter.provider.anthropic;

import com.limecoding.llmrouter.core.model.FinishReason;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicResponseMappingTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final AnthropicMapper mapper = new AnthropicMapper(json);

    @Test
    void preservesMixedTextAndMultipleToolCalls() {
        var response = mapper.toDomain(response("""
                [
                  {"type":"tool_use","id":"call-1","name":"weather",
                   "input":{"city":"서울","options":{"units":["celsius"]}}},
                  {"type":"text","text":"Checking "},
                  {"type":"tool_use","id":"call-2","name":"time","input":{}},
                  {"type":"text","text":"now."}
                ]
                """, "tool_use"));

        assertEquals("Checking now.", response.message().content());
        var calls = response.message().toolCalls();
        assertEquals(2, calls.size());
        assertEquals("call-1", calls.getFirst().id());
        assertEquals("function", calls.getFirst().type());
        assertEquals("weather", calls.getFirst().function().name());
        assertEquals(json.readTree("""
                {"city":"서울","options":{"units":["celsius"]}}
                """), json.readTree(calls.getFirst().function().arguments()));
        assertEquals("call-2", calls.get(1).id());
        assertEquals("time", calls.get(1).function().name());
        assertEquals("{}", calls.get(1).function().arguments());
        assertEquals(FinishReason.TOOL_CALLS, response.finishReason());
    }

    @Test
    void acceptsToolOnlyResponseWithoutText() {
        var response = mapper.toDomain(response("""
                [{"type":"tool_use","id":"call-1","name":"ping","input":{}}]
                """, "tool_use"));
        assertNull(response.message().content());
        assertEquals(1, response.message().toolCalls().size());
    }

    @Test
    void preservesTextOnlyResponseAndMetadata() {
        var response = mapper.toDomain(response("""
                [{"type":"text","text":"Hello"},{"type":"text","text":" world"}]
                """, "end_turn"));
        assertEquals("Hello world", response.message().content());
        assertTrue(response.message().toolCalls().isEmpty());
        assertEquals("msg-1", response.id());
        assertEquals("test", response.model());
        assertEquals(FinishReason.STOP, response.finishReason());
        assertEquals(10, response.usage().promptTokens());
        assertEquals(5, response.usage().completionTokens());
    }

    @Test
    void acceptsEmptyContent() {
        var response = mapper.toDomain(response("[]", "end_turn"));
        assertNull(response.message().content());
        assertTrue(response.message().toolCalls().isEmpty());
    }

    @Test
    void rejectsToolResultInAssistantResponse() {
        var wire = response("""
                [{"type":"tool_result","tool_use_id":"call-1","content":"done"}]
                """, "end_turn");
        assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(wire));
    }

    private AnthropicResponse response(String content, String stopReason) {
        return json.readValue("""
                {"id":"msg-1","model":"test","role":"assistant","type":"message",
                 "content":%s,"stop_reason":"%s","usage":{
                 "input_tokens":10,"output_tokens":5,
                 "cache_creation_input_tokens":0,"cache_read_input_tokens":0}}
                """.formatted(content, stopReason), AnthropicResponse.class);
    }
}
