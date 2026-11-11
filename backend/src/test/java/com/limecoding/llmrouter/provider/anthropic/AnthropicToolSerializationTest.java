package com.limecoding.llmrouter.provider.anthropic;

import com.limecoding.llmrouter.core.model.ChatRequest;
import com.limecoding.llmrouter.core.model.Message;
import com.limecoding.llmrouter.core.model.Tool;
import com.limecoding.llmrouter.provider.anthropic.wire.AnthropicTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicToolSerializationTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void omitsUnsetExamplesFromMappedRequest(boolean streaming) {
        var schema = json.readTree("{\"type\":\"object\"}");
        var tool = new Tool("function", new Tool.Function("weather", "Weather", schema, null));
        var request = new ChatRequest("test", List.of(Message.user("weather")), List.of(tool), null, 100, null);
        var wire = new AnthropicMapper(json).toWire(request, streaming);
        var actual = json.readTree(json.writeValueAsString(wire)).get("tools").get(0);

        assertEquals(json.readTree("""
                {"name":"weather","description":"Weather","input_schema":{"type":"object"}}
                """), actual);
    }

    @Test
    void serializesExamplesWithPluralFieldName() {
        var example = json.readTree("{\"location\":\"Seoul\"}");
        var tool = new AnthropicTool("weather", "Weather", json.readTree("{\"type\":\"object\"}"), List.of(example));
        var actual = json.readTree(json.writeValueAsString(tool));

        assertFalse(actual.has("input_example"));
        assertEquals(json.readTree("[{\"location\":\"Seoul\"}]"), actual.get("input_examples"));
    }
}
