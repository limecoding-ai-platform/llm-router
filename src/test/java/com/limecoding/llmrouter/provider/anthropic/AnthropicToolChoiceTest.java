package com.limecoding.llmrouter.provider.anthropic;

import com.limecoding.llmrouter.core.model.ChatRequest;
import com.limecoding.llmrouter.core.model.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicToolChoiceTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final AnthropicMapper mapper = new AnthropicMapper();

    @ParameterizedTest
    @CsvSource({"auto,auto", "none,none", "required,any"})
    void convertsStringChoiceToAnthropicJson(String input, String expectedType) {
        assertChoice(json.getNodeFactory().stringNode(input),
                json.readTree("{\"type\":\"" + expectedType + "\"}"));
    }

    @Test
    void convertsNamedFunctionToAnthropicJson() {
        assertChoice(json.readTree("""
                {"type":"function","function":{"name":"get_weather"}}
                """), json.readTree("""
                {"type":"tool","name":"get_weather"}
                """));
    }

    @Test
    void omitsAbsentAndExplicitNullChoice() {
        assertChoice(null, null);
        assertChoice(json.readTree("null"), null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"any\"", "\"\"", "42", "true", "[]", "{}",
            "{\"type\":\"tool\",\"function\":{\"name\":\"weather\"}}",
            "{\"type\":\"function\"}",
            "{\"type\":\"function\",\"function\":{\"name\":\" \"}}",
            "{\"type\":\"function\",\"function\":{\"name\":42}}"})
    void rejectsUnsupportedOrMalformedChoice(String input) {
        var request = request(json.readTree(input));
        assertThrows(IllegalArgumentException.class, () -> mapper.toWire(request));
        assertThrows(IllegalArgumentException.class, () -> mapper.toWire(request, true));
    }

    private void assertChoice(JsonNode input, JsonNode expected) {
        var request = request(input);
        for (var wire : List.of(mapper.toWire(request), mapper.toWire(request, true))) {
            JsonNode actual = json.readTree(json.writeValueAsString(wire));
            assertFalse(actual.has("toolChoice"));
            assertEquals(expected, actual.get("tool_choice"));
        }
    }

    private ChatRequest request(JsonNode choice) {
        return new ChatRequest("test", List.of(Message.user("hello")),
                List.of(), choice, 1024, null);
    }
}
