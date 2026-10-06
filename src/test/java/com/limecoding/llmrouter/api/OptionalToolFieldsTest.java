package com.limecoding.llmrouter.api;

import com.limecoding.llmrouter.core.ProviderAdapter;
import com.limecoding.llmrouter.core.ProviderRegistry;
import com.limecoding.llmrouter.core.model.*;
import com.limecoding.llmrouter.provider.openai.MaxTokensParam;
import com.limecoding.llmrouter.provider.openai.OpenAiMapper;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class OptionalToolFieldsTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final OpenAiMapper mapper = new OpenAiMapper(Map.of("test", MaxTokensParam.MAX_TOKENS));

    @Test
    void acceptsOmittedNullAndEmptyToolFields() throws Exception {
        var adapter = mock(ProviderAdapter.class);
        when(adapter.id()).thenReturn("openai");
        when(adapter.chat(any())).thenAnswer(invocation -> {
            ChatRequest request = invocation.getArgument(0);
            assertTrue(request.tools().isEmpty());
            assertTrue(request.messages().getFirst().toolCalls().isEmpty());
            assertTrue(mapper.toWire(request).tools().isEmpty());
            return new ChatResponse("id", "test", Message.assistant("ok", null, null, null),
                    FinishReason.STOP, new Usage(1, 1));
        });
        var mvc = MockMvcBuilders.standaloneSetup(
                new ChatCompletionsController(new ProviderRegistry(List.of(adapter)))).build();
        for (String fields : List.of("", ",\"tool_calls\":null", ",\"tool_calls\":[]")) {
            for (String tools : List.of("", ",\"tools\":null", ",\"tools\":[]")) {
                mvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"openai/test\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\""
                                + fields + "}]" + tools + "}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.choices[0].message.content").value("ok"))
                        .andExpect(jsonPath("$.choices[0].message.tool_calls").doesNotExist());
            }
        }
    }

    @Test
    void includesToolCallsInHttpResponse() throws Exception {
        var upstream = json.readValue("""
                {"id":"id","created":0,"model":"test","choices":[{"index":0,
                "message":{"role":"assistant","content":null,"tool_calls":[
                {"id":"call-1","type":"function","function":{"name":"get_weather","arguments":"{\\"city\\":\\"Seoul\\"}"}},
                {"id":"call-2","type":"function","function":{"name":"get_weather","arguments":"{\\"city\\":\\"Busan\\"}"}}
                ]},"finish_reason":"tool_calls"}],
                "usage":{"prompt_tokens":51,"completion_tokens":14,"total_tokens":65}}
                """, OpenAiResponse.class);
        var adapter = mock(ProviderAdapter.class);
        when(adapter.id()).thenReturn("openai");
        when(adapter.chat(any())).thenReturn(mapper.toDomain(upstream));
        var mvc = MockMvcBuilders.standaloneSetup(
                new ChatCompletionsController(new ProviderRegistry(List.of(adapter)))).build();
        var result = mvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"openai/test","messages":[{"role":"user","content":"weather"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].finish_reason").value("tool_calls"))
                .andReturn();
        var message = json.readTree(result.getResponse().getContentAsString())
                .get("choices").get(0).get("message");
        assertEquals("assistant", message.get("role").asText());
        assertTrue(message.get("content").isNull());
        assertEquals(json.readTree(json.writeValueAsString(upstream.choices().getFirst().message().toolCalls())),
                message.get("tool_calls"));
    }

    @Test
    void rejectsMissingNullEmptyOrNullElementMessages() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(
                new ChatCompletionsController(new ProviderRegistry(List.of()))).build();
        for (String messages : List.of("", ",\"messages\":null", ",\"messages\":[]", ",\"messages\":[null]")) {
            mvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"model\":\"openai/test\"" + messages + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void mapsTextResponsesWithoutToolCalls() {
        for (String fields : List.of("", ",\"tool_calls\":null", ",\"tool_calls\":[]")) {
            var response = json.readValue("""
                    {"id":"id","created":0,"model":"test","choices":[{"index":0,
                    "message":{"role":"assistant","content":"ok"%s},"finish_reason":"stop"}],
                    "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                    """.formatted(fields), OpenAiResponse.class);
            assertTrue(mapper.toDomain(response).message().toolCalls().isEmpty());
        }
    }

    @Test
    void omitsEmptyToolFieldsFromUpstreamRequestsInBothModes() {
        var request = new ChatRequest("test", List.of(Message.user("hello")), null, null, null, null);
        for (var wire : List.of(mapper.toWire(request), mapper.toWire(request, true))) {
            var tree = json.readTree(json.writeValueAsString(wire));
            assertFalse(tree.has("tools"));
            assertFalse(tree.get("messages").get(0).has("tool_calls"));
        }
    }

    @Test
    void preservesPopulatedToolCalls() {
        var response = json.readValue("""
                {"id":"id","created":0,"model":"test","choices":[{"index":0,"message":{"role":"assistant",
                "tool_calls":[{"id":"call-1","type":"function","function":{"name":"weather","arguments":"{}"}}]},
                "finish_reason":"tool_calls"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """, OpenAiResponse.class);
        var message = mapper.toDomain(response).message();
        var wire = mapper.toWire(new ChatRequest("test", List.of(message), null, null, null, null));
        assertEquals(response.choices().getFirst().message().toolCalls(), wire.messages().getFirst().toolCalls());
    }
}
