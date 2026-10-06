package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.api.ChatCompletionsController;
import com.limecoding.llmrouter.core.ProviderRegistry;
import com.limecoding.llmrouter.core.model.ChatRequest;
import com.limecoding.llmrouter.core.model.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OpenAiStreamingTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final OpenAiMapper mapper = new OpenAiMapper(Map.of("test", MaxTokensParam.MAX_TOKENS));
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://openai.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenAiAdapter adapter = new OpenAiAdapter(mapper, new OpenAiStreamMapper(), json, builder.build());

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void forwardsToolResultsWithOriginalCallIds(boolean streaming) throws Exception {
        server.expect(requestTo("https://openai.test/chat/completions"))
                .andExpect(request -> {
                    var body = json.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertEquals(streaming, body.get("stream").asBoolean());
                    var messages = body.get("messages");
                    assertEquals(4, messages.size());
                    assertFalse(messages.get(0).has("tool_call_id"));
                    assertFalse(messages.get(1).has("tool_call_id"));
                    var calls = messages.get(1).get("tool_calls");
                    for (int i = 0; i < 2; i++) {
                        var result = messages.get(i + 2);
                        assertEquals("tool", result.get("role").stringValue());
                        assertEquals(calls.get(i).get("id"), result.get("tool_call_id"));
                        assertEquals(i == 0 ? "서울 23도" : "부산 25도", result.get("content").stringValue());
                        assertFalse(result.has("tool_calls"));
                    }
                })
                .andRespond(withSuccess(streaming
                        ? chunk(0, "{\"content\":\"weather received\"}", "stop") + "data: [DONE]\n\n"
                        : """
                        {"id":"id","created":0,"model":"test","choices":[{"index":0,
                        "message":{"role":"assistant","content":"weather received"},"finish_reason":"stop"}],
                        "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                        """, streaming ? MediaType.TEXT_EVENT_STREAM : MediaType.APPLICATION_JSON));
        String body = """
                {"model":"openai/test","stream":%s,"messages":[
                {"role":"user","content":"서울과 부산 날씨"},
                {"role":"assistant","tool_calls":[
                  {"id":"call-a","type":"function","function":{"name":"get_weather","arguments":"{}"}},
                  {"id":"call-b","type":"function","function":{"name":"get_weather","arguments":"{}"}}]},
                {"role":"tool","tool_call_id":"call-a","content":"서울 23도"},
                {"role":"tool","tool_call_id":"call-b","content":"부산 25도"}]}
                """.formatted(streaming);
        if (streaming) {
            assertEquals("weather received", stream(body).getFirst().get("choices").get(0).get("delta").get("content").stringValue());
        } else {
            var mvc = MockMvcBuilders.standaloneSetup(new ChatCompletionsController(new ProviderRegistry(List.of(adapter)))).build();
            mvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.choices[0].message.content").value("weather received"));
        }
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsToolResultsWithoutCallId(boolean streaming) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ChatCompletionsController(new ProviderRegistry(List.of(adapter)))).build();
        for (String field : List.of("", ",\"tool_call_id\":null", ",\"tool_call_id\":\"\"", ",\"tool_call_id\":\"  \"")) {
            mvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"model\":\"openai/test\",\"stream\":" + streaming
                                    + ",\"messages\":[{\"role\":\"tool\",\"content\":\"result\"" + field + "}]}"))
                    .andExpect(status().isBadRequest());
        }
        server.verify();
    }

    @Test
    void streamsInterleavedToolArgumentsAndUsageThroughHttp() throws Exception {
        String upstream = ": keepalive\r\n\r\n"
                + chunk(2, "{\"role\":\"assistant\",\"content\":\"\"}", null)
                + chunk(2, tools(
                        Map.of("index", 0, "id", "call-a", "type", "function", "function", Map.of("name", "get_weather", "arguments", "")),
                        Map.of("index", 1, "id", "call-b", "type", "function", "function", Map.of("name", "get_weather", "arguments", ""))), null)
                + chunk(2, tools(Map.of("index", 0, "function", Map.of("arguments", "{\"city\":\"서"))), null)
                + chunk(2, tools(Map.of("index", 1, "function", Map.of("arguments", "{\"city\":\"부산\"}"))), null)
                + chunk(2, tools(Map.of("index", 0, "function", Map.of("arguments", "울\"}"))), null)
                + chunk(2, "{}", "tool_calls")
                + """
                data: {"choices":[],"usage":{"prompt_tokens":51,"completion_tokens":14,"total_tokens":65,
                data: "prompt_tokens_details":{"cached_tokens":10},"completion_tokens_details":{"reasoning_tokens":3}}}

                data: [DONE]

                """;
        server.expect(requestTo("https://openai.test/chat/completions"))
                .andExpect(request -> {
                    var body = json.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertTrue(body.get("stream").asBoolean());
                    assertTrue(body.get("stream_options").get("include_usage").asBoolean());
                    assertEquals("get_weather", body.get("tools").get(0).get("function").get("name").stringValue());
                    assertEquals("auto", body.get("tool_choice").stringValue());
                    assertEquals(50, body.get("max_tokens").intValue());
                })
                .andRespond(withSuccess(upstream, MediaType.TEXT_EVENT_STREAM));
        var chunks = stream("""
                {"model":"openai/test","stream":true,"stream_options":{"include_usage":true},
                 "messages":[{"role":"user","content":"서울과 부산 날씨"}],"max_tokens":50,
                 "tools":[{"type":"function","function":{"name":"get_weather","parameters":{"type":"object"}}}],
                 "tool_choice":"auto"}
                """);
        var arguments = new StringBuilder[]{new StringBuilder(), new StringBuilder()};
        int initialCalls = 0;
        for (var response : chunks) {
            assertEquals(chunks.getFirst().get("id"), response.get("id"));
            assertEquals(chunks.getFirst().get("created"), response.get("created"));
            assertEquals("chat.completion.chunk", response.get("object").stringValue());
            for (var choice : response.get("choices")) {
                assertEquals(2, choice.get("index").intValue());
                var delta = choice.get("delta");
                if (delta.has("tool_calls")) {
                    for (var call : delta.get("tool_calls")) {
                        int index = call.get("index").intValue();
                        if (call.has("id")) {
                            initialCalls++;
                            assertEquals(index == 0 ? "call-a" : "call-b", call.get("id").stringValue());
                            assertEquals("function", call.get("type").stringValue());
                        } else {
                            assertFalse(call.has("type"));
                            assertFalse(call.get("function").has("name"));
                        }
                        arguments[index].append(call.get("function").get("arguments").stringValue());
                    }
                }
            }
        }
        assertEquals(2, initialCalls);
        assertEquals("{\"city\":\"서울\"}", arguments[0].toString());
        assertEquals("{\"city\":\"부산\"}", arguments[1].toString());
        assertEquals("assistant", chunks.getFirst().get("choices").get(0).get("delta").get("role").stringValue());
        var finished = chunks.get(chunks.size() - 2).get("choices").get(0);
        assertTrue(finished.get("delta").isEmpty());
        assertEquals("tool_calls", finished.get("finish_reason").stringValue());
        var usage = chunks.getLast();
        assertTrue(usage.get("choices").isEmpty());
        assertEquals(65, usage.get("usage").get("total_tokens").intValue());
        assertEquals(10, usage.get("usage").get("prompt_tokens_details").get("cached_tokens").intValue());
        assertEquals(3, usage.get("usage").get("completion_tokens_details").get("reasoning_tokens").intValue());
        server.verify();
    }

    @Test
    void streamsTextAndRefusalWithoutUsageByDefault() throws Exception {
        server.expect(requestTo("https://openai.test/chat/completions"))
                .andExpect(request -> assertFalse(json.readTree(((MockClientHttpRequest) request).getBodyAsString()).has("stream_options")))
                .andRespond(withSuccess(chunk(1, "{\"role\":\"assistant\",\"content\":\"hello\",\"refusal\":\"refused\"}", "content_filter")
                        + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}\n\n"
                        + "data: [DONE]", MediaType.TEXT_EVENT_STREAM));
        var chunks = stream("""
                {"model":"openai/test","stream":true,"messages":[{"role":"user","content":"hello"}]}
                """);
        assertEquals(4, chunks.size());
        assertEquals("hello", chunks.get(1).get("choices").get(0).get("delta").get("content").stringValue());
        assertEquals("refused", chunks.get(2).get("choices").get(0).get("delta").get("refusal").stringValue());
        for (var chunk : chunks) {
            assertEquals(1, chunk.get("choices").get(0).get("index").intValue());
            assertFalse(chunk.hasNonNull("usage"));
        }
        server.verify();
    }

    @Test
    void failsOnTruncatedStreamInsteadOfReportingSuccess() {
        server.expect(requestTo("https://openai.test/chat/completions"))
                .andRespond(withSuccess(chunk(0, "{}", "stop"), MediaType.TEXT_EVENT_STREAM));
        assertThrows(RuntimeException.class, () -> adapter.stream(request(false), event -> {}));
        server.verify();
    }

    @Test
    void failsOnInStreamError() {
        server.expect(requestTo("https://openai.test/chat/completions"))
                .andRespond(withSuccess("data: {\"error\":{\"message\":\"failed\"}}\n\ndata: [DONE]\n\n", MediaType.TEXT_EVENT_STREAM));
        assertThrows(RuntimeException.class, () -> adapter.stream(request(false), event -> {}));
        server.verify();
    }

    @Test
    void preservesUpstreamHttpError() {
        server.expect(requestTo("https://openai.test/chat/completions"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":{\"message\":\"rate limited\"}}").contentType(MediaType.APPLICATION_JSON));
        var error = assertThrows(RestClientResponseException.class, () -> adapter.stream(request(false), event -> {}));
        assertEquals(429, error.getStatusCode().value());
        assertTrue(error.getResponseBodyAsString().contains("rate limited"));
        server.verify();
    }

    @Test
    void includesUsageOnlyForRequestedStreamingAndValidatesModels() {
        assertNull(mapper.toWire(request(true)).streamOptions());
        assertNull(mapper.toWire(request(false), true).streamOptions());
        assertTrue(mapper.toWire(request(true), true).streamOptions().includeUsage());
        assertThrows(IllegalArgumentException.class, () -> mapper.toWire(
                new ChatRequest("missing", List.of(Message.user("hello")), null, null, null, null), true));
    }

    private ChatRequest request(boolean usage) {
        return new ChatRequest("test", List.of(Message.user("hello")), null, null, 50, null, usage);
    }

    private List<JsonNode> stream(String body) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ChatCompletionsController(new ProviderRegistry(List.of(adapter)))).build();
        var pending = mvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
        pending.getAsyncResult(5000);
        var response = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)).andReturn();
        var payloads = response.getResponse().getContentAsString(StandardCharsets.UTF_8).lines()
                .filter(line -> line.startsWith("data:")).map(line -> line.substring(5).stripLeading()).toList();
        assertEquals("[DONE]", payloads.getLast());
        assertEquals(1, payloads.stream().filter("[DONE]"::equals).count());
        return payloads.subList(0, payloads.size() - 1).stream().map(json::readTree).toList();
    }

    private String chunk(int index, String delta, String finish) {
        return "data: {\"choices\":[{\"index\":" + index + ",\"delta\":" + delta
                + ",\"finish_reason\":" + json.writeValueAsString(finish) + "}]}\n\n";
    }

    @SafeVarargs
    private final String tools(Map<String, Object>... calls) {
        return json.writeValueAsString(Map.of("tool_calls", List.of(calls)));
    }
}
