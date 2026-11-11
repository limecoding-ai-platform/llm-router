package com.limecoding.llmrouter.provider.anthropic;

import com.limecoding.llmrouter.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicRequestMappingTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final AnthropicMapper mapper = new AnthropicMapper(json);

    @Test
    void serializesTextMessagesAsBlocks() {
        assertMessages(List.of(Message.user("hello"), Message.assistant("hi", null, null, null)), """
                [{"role":"user","content":[{"type":"text","text":"hello"}]},
                 {"role":"assistant","content":[{"type":"text","text":"hi"}]}]
                """);
    }

    @Test
    void serializesToolHistoryAndGroupsConsecutiveResults() {
        assertMessages(List.of(
                Message.user("날씨와 시간"),
                Message.assistant("확인할게요", List.of(
                        call("a", "weather", "{\"city\":\"서울\",\"options\":{\"units\":[\"celsius\"]}}"),
                        call("b", "time", "{}")), null, null),
                result("a", "20도"), result("b", "12시"), Message.user("고마워")), """
                [
                  {"role":"user","content":[{"type":"text","text":"날씨와 시간"}]},
                  {"role":"assistant","content":[
                    {"type":"text","text":"확인할게요"},
                    {"type":"tool_use","id":"a","name":"weather",
                     "input":{"city":"서울","options":{"units":["celsius"]}}},
                    {"type":"tool_use","id":"b","name":"time","input":{}}]},
                  {"role":"user","content":[
                    {"type":"tool_result","tool_use_id":"a","content":"20도"},
                    {"type":"tool_result","tool_use_id":"b","content":"12시"}]},
                  {"role":"user","content":[{"type":"text","text":"고마워"}]}
                ]
                """);
    }

    @Test
    void serializesToolOnlyAssistantAndKeepsSeparateRounds() {
        var assistant = Message.assistant(null, List.of(call("a", "ping", "{}")), null, null);
        var next = Message.assistant("", List.of(call("b", "ping", "{}")), null, null);
        assertMessages(List.of(assistant, result("a", ""), next, result("b", "ok")), """
                [{"role":"assistant","content":[{"type":"tool_use","id":"a","name":"ping","input":{}}]},
                 {"role":"user","content":[{"type":"tool_result","tool_use_id":"a","content":""}]},
                 {"role":"assistant","content":[{"type":"tool_use","id":"b","name":"ping","input":{}}]},
                 {"role":"user","content":[{"type":"tool_result","tool_use_id":"b","content":"ok"}]}]
                """);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "{broken", "[]", "42", "null", "{} {}"})
    void rejectsArgumentsThatAreNotOneJsonObject(String arguments) {
        var request = request(List.of(Message.assistant(null,
                List.of(call("a", "ping", arguments)), null, null)));
        assertThrows(IllegalArgumentException.class, () -> mapper.toWire(request));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void rejectsResultWithoutCallId(String id) {
        assertThrows(IllegalArgumentException.class, () -> mapper.toWire(request(List.of(result(id, "ok")))));
    }

    private void assertMessages(List<Message> messages, String expected) {
        var request = request(messages);
        for (var wire : List.of(mapper.toWire(request), mapper.toWire(request, true))) {
            JsonNode actual = json.readTree(json.writeValueAsString(wire));
            assertEquals(json.readTree(expected), actual.get("messages"));
        }
    }

    private ChatRequest request(List<Message> messages) {
        return new ChatRequest("test", messages, List.of(), null, 1024, null);
    }

    private Message.ToolCall call(String id, String name, String arguments) {
        return new Message.ToolCall(id, "function", new Message.FunctionCall(name, arguments));
    }

    private Message result(String id, String content) {
        return new Message(Role.TOOL, content, null, null, null, id);
    }
}
