package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.api.*;
import com.limecoding.llmrouter.api.responses.ResponsesMapper;
import com.limecoding.llmrouter.core.ProviderRegistry;
import com.limecoding.llmrouter.core.model.*;
import com.limecoding.llmrouter.provider.openai.responses.OpenAiResponsesMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OpenAiResponsesTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final OpenAiResponsesMapper mapper = new OpenAiResponsesMapper();
    static final String RESPONSE = """
        {"id":"resp_upstream","model":"test","status":"completed","output":[
          {"type":"reasoning","id":"rs_1","summary":[]},
          {"type":"message","id":"msg_1","role":"assistant","content":[{"type":"output_text","text":"hello"}]},
          {"type":"function_call","id":"fc_1","call_id":"call_1","name":"weather","arguments":"{}"}],
         "usage":{"input_tokens":12,"output_tokens":7,"total_tokens":19,
         "input_tokens_details":{"cached_tokens":3},"output_tokens_details":{"reasoning_tokens":2}}}
        """;

    @Test void mapsRequestWithoutChatCompletionsFields() {
        var domain = new ResponsesMapper().toDomain(json.readTree("""
            {"input":[{"role":"user","content":"weather"},
             {"type":"function_call","call_id":"c1","name":"weather","arguments":"{}"},
             {"type":"function_call_output","call_id":"c1","output":"sunny"}],
             "instructions":"Be concise","max_output_tokens":100,"temperature":0.5,
             "tools":[{"type":"function","name":"weather","parameters":{"type":"object"},"strict":true}],
             "tool_choice":{"type":"function","name":"weather"}}
            """), "test");
        assertEquals(ApiType.RESPONSES, domain.apiType());
        var wire = json.valueToTree(mapper.toWire(domain, true));
        assertFalse(wire.has("messages"));
        assertFalse(wire.has("max_tokens"));
        assertFalse(wire.has("max_completion_tokens"));
        assertFalse(wire.has("stream_options"));
        assertFalse(wire.path("store").asBoolean());
        assertEquals("system", wire.at("/input/0/role").asText());
        assertEquals("function_call", wire.at("/input/2/type").asText());
        assertEquals("c1", wire.at("/input/3/call_id").asText());
        assertEquals("sunny", wire.at("/input/3/output").asText());
        assertEquals("weather", wire.at("/tools/0/name").asText());
        assertFalse(wire.at("/tools/0").has("function"));
        assertEquals("weather", wire.at("/tool_choice/name").asText());
        assertEquals(100, wire.path("max_output_tokens").asInt());
        assertEquals(0.5, wire.path("temperature").asDouble());
    }

    @Test void parsesResponseCallIdAndIncompleteOrFailureStatus() {
        var response = mapper.toDomain(json.readTree(RESPONSE));
        assertEquals("hello", response.message().content());
        assertEquals("call_1", response.message().toolCalls().getFirst().id());
        assertEquals(FinishReason.TOOL_CALLS, response.finishReason());
        assertEquals(19, response.usage().totalTokens());
        var incomplete = json.readTree(RESPONSE).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) incomplete).put("status", "incomplete")
                .putObject("incomplete_details").put("reason", "max_output_tokens");
        assertEquals(FinishReason.LENGTH, mapper.toDomain(incomplete).finishReason());
        for (String status : List.of("failed", "in_progress", "cancelled")) {
            var invalid = json.readTree(RESPONSE).deepCopy();
            ((tools.jackson.databind.node.ObjectNode) invalid).put("status", status);
            assertThrows(RuntimeException.class, () -> mapper.toDomain(invalid));
        }
    }

    @Test void assistantHistoryUsesPlainStringIncludingRefusal() {
        var domain = new ChatRequest("test", List.of(Message.user("hi"),
                Message.assistant("answer", List.of(), "Cannot help with that part", null), Message.user("continue")),
                null, null, null, null, false, ApiType.RESPONSES);
        var wire = json.valueToTree(mapper.toWire(domain, false));
        var assistant = wire.at("/input/1");
        assertEquals("assistant", assistant.path("role").asText());
        assertTrue(assistant.path("content").isString());
        assertEquals("answer\n\nCannot help with that part", assistant.path("content").asText());
    }

    @Test void bothIngressPathsUseTheirOwnUpstreamEndpoint() throws Exception {
        var builder = RestClient.builder().baseUrl("https://openai.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var adapter = adapter(builder, Set.of(ApiType.CHAT_COMPLETIONS, ApiType.RESPONSES));
        var registry = new ProviderRegistry(List.of(adapter));
        var mvc = MockMvcBuilders.standaloneSetup(new ResponsesController(registry), new ChatCompletionsController(registry))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        server.expect(requestTo("https://openai.test/responses"))
                .andExpect(request -> assertTrue(json.readTree(((MockClientHttpRequest)request).getBodyAsString()).has("input")))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openai.test/chat/completions"))
                .andRespond(withSuccess("""
                    {"id":"chat_1","model":"test","created":1,"choices":[{"index":0,"message":{"role":"assistant","content":"chat"},"finish_reason":"stop"}],
                    "usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}
                    """, MediaType.APPLICATION_JSON));
        mvc.perform(post("/v1/responses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"openai/test\",\"input\":\"hi\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.output[0].content[0].text").value("hello"));
        mvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"openai/test\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.choices[0].message.content").value("chat"));
        server.verify();
    }

    @Test void unsupportedApiIs400BeforeStreamingAndBeforeUpstreamCall() throws Exception {
        for (ApiType supported : ApiType.values()) {
            var builder = RestClient.builder().baseUrl("https://openai.test");
            var server = MockRestServiceServer.bindTo(builder).build();
            var registry = new ProviderRegistry(List.of(adapter(builder, Set.of(supported))));
            var mvc = MockMvcBuilders.standaloneSetup(new ResponsesController(registry), new ChatCompletionsController(registry))
                    .setControllerAdvice(new ApiExceptionHandler()).build();
            boolean responses = supported == ApiType.CHAT_COMPLETIONS;
            for (boolean stream : List.of(false, true)) {
                String body = "{\"model\":\"openai/test\",\"stream\":" + stream + "," +
                        (responses ? "\"input\":\"hi\"" : "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]") + "}";
                mvc.perform(post(responses ? "/v1/responses" : "/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest()).andExpect(request().asyncNotStarted())
                        .andExpect(jsonPath("$.error.code").value("unsupported_api"));
            }
            server.verify();
        }
    }

    private OpenAiAdapter adapter(RestClient.Builder builder, Set<ApiType> supported) {
        return new OpenAiAdapter(new OpenAiMapper(Map.of("test", MaxTokensParam.MAX_TOKENS)),
                new OpenAiStreamMapper(), json, builder.build(), Map.of("test", supported));
    }
}
