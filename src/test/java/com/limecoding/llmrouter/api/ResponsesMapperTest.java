package com.limecoding.llmrouter.api;

import com.limecoding.llmrouter.api.responses.ResponsesMapper;
import com.limecoding.llmrouter.core.model.*;
import com.limecoding.llmrouter.provider.anthropic.AnthropicMapper;
import com.limecoding.llmrouter.provider.openai.OpenAiMapper;
import com.limecoding.llmrouter.provider.openai.MaxTokensParam;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ResponsesMapperTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final ResponsesMapper mapper = new ResponsesMapper();

    @Test void mapsInstructionsAndOptionsThroughBothProviders() {
        var request = mapper.toDomain(json.readTree("""
            {"model":"openai/test","input":"hello","instructions":"Be concise",
             "max_output_tokens":100,"temperature":0.4,"stream":true,"store":false}
            """), "test");
        assertEquals("SYSTEM", request.messages().getFirst().role().name());
        assertEquals("hello", request.messages().getLast().content());
        assertTrue(request.includeUsage());
        var openai = json.valueToTree(new OpenAiMapper(Map.of("test", MaxTokensParam.MAX_TOKENS)).toWire(request));
        assertEquals("system", openai.at("/messages/0/role").asText());
        assertEquals(0.4, openai.path("temperature").asDouble());
        var anthropic = json.valueToTree(new AnthropicMapper(json).toWire(request));
        assertEquals("Be concise", anthropic.path("system").asText());
        assertEquals(1, anthropic.path("messages").size());
        assertEquals(0.4, anthropic.path("temperature").asDouble());
    }

    @Test void mapsParallelCallsAndResults() {
        var request = mapper.toDomain(json.readTree("""
            {"input":[{"role":"user","content":[{"type":"input_text","text":"weather"}]},
              {"type":"function_call","call_id":"c1","name":"weather","arguments":"{}"},
              {"type":"function_call","call_id":"c2","name":"weather","arguments":"{}"},
              {"type":"function_call_output","call_id":"c1","output":"sun"},
              {"type":"function_call_output","call_id":"c2","output":"rain"}],
             "tools":[{"type":"function","name":"weather","parameters":{"type":"object"},"strict":true}],
             "tool_choice":{"type":"function","name":"weather"}}
            """), "test");
        assertEquals(4, request.messages().size());
        assertEquals(2, request.messages().get(1).toolCalls().size());
        assertEquals("c1", request.messages().get(2).toolCallId());
        assertEquals("c2", request.messages().get(3).toolCallId());
        assertEquals("weather", request.toolChoice().at("/function/name").asText());
        assertTrue(request.tools().getFirst().function().strict());
    }

    @Test void rejectsMalformedAndUnsupportedInputs() {
        for (String body : List.of("{}", "{\"input\":[]}", "{\"input\":[null]}",
                "{\"input\":7}", "{\"input\":\"hi\",\"stream\":\"true\"}",
                "{\"input\":\"hi\",\"max_output_tokens\":1.5}",
                "{\"input\":\"hi\",\"temperature\":3}",
                "{\"input\":\"hi\",\"previous_response_id\":\"resp_1\"}",
                "{\"input\":\"hi\",\"reasoning\":{\"effort\":\"high\"}}",
                "{\"input\":\"hi\",\"store\":true}",
                "{\"input\":\"hi\",\"tools\":[{\"type\":\"web_search\"}]}",
                "{\"input\":[{\"role\":\"user\",\"content\":[{\"type\":\"input_image\",\"image_url\":\"x\"}]}]}",
                "{\"input\":[{\"type\":\"function_call\",\"call_id\":\"a\",\"name\":\"f\",\"arguments\":\"[]\"}]}",
                "{\"input\":[{\"type\":\"function_call_output\",\"call_id\":\"a\",\"output\":\"x\"}]}")) {
            assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(json.readTree(body), "test"), body);
        }
    }

    @Test void outputsTextToolsUsageAndIncompleteStatus() {
        var chat = new ChatResponse("upstream", "test", Message.assistant("hello", List.of(
            new Message.ToolCall("c1", "function", new Message.FunctionCall("f", "{}"))), null, null),
            FinishReason.LENGTH, new Usage(2, 3));
        var result = json.valueToTree(mapper.response(chat, "openai/test"));
        assertEquals("response", result.path("object").asText());
        assertTrue(result.path("id").asText().startsWith("resp_"));
        assertEquals("incomplete", result.path("status").asText());
        assertEquals("max_output_tokens", result.at("/incomplete_details/reason").asText());
        assertEquals("hello", result.at("/output/0/content/0/text").asText());
        assertEquals("c1", result.at("/output/1/call_id").asText());
        assertEquals(5, result.at("/usage/total_tokens").asInt());
        assertFalse(result.path("store").asBoolean());
    }

    @Test void acceptsItsOwnTextAndRefusalOutputInConversationHistory() {
        for (String refusal : List.of("", "Cannot help")) {
            var response = mapper.response(new ChatResponse("upstream", "test",
                    Message.assistant("hello", null, refusal.isEmpty() ? null : refusal, null),
                    FinishReason.STOP, new Usage(1, 1)), "openai/test");
            var body = json.createObjectNode();
            var input = body.putArray("input");
            input.addObject().put("role", "user").put("content", "hi");
            for (var item : response.output()) input.add(json.valueToTree(item));
            input.addObject().put("role", "user").put("content", "continue");
            var domain = mapper.toDomain(body, "test");
            assertEquals(3, domain.messages().size());
            assertEquals("hello", domain.messages().get(1).content());
            assertEquals(refusal.isEmpty() ? null : refusal, domain.messages().get(1).refusal());
        }
    }

    @Test void preservesRefusalHistoryWhenForwardingToAnthropic() {
        var domain = mapper.toDomain(json.readTree("""
                {"input":[{"role":"user","content":"hi"},
                  {"type":"message","role":"assistant","content":[{"type":"refusal","refusal":"Cannot help"}]},
                  {"role":"user","content":"another question"}]}
                """), "test");
        for (boolean stream : List.of(false, true)) {
            var wire = json.valueToTree(new AnthropicMapper(json).toWire(domain, stream));
            assertEquals("Cannot help", wire.at("/messages/1/content/0/text").asText());
        }
    }
}
