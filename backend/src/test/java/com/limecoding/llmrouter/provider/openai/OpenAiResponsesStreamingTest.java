package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.api.*;
import com.limecoding.llmrouter.core.ProviderRegistry;
import com.limecoding.llmrouter.core.model.*;
import com.limecoding.llmrouter.provider.openai.responses.OpenAiResponsesStreamMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OpenAiResponsesStreamingTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void mapsSparseOutputIndexesAndDoesNotDuplicateDoneArguments() {
        var mapper = new OpenAiResponsesStreamMapper();
        List<StreamEvent> events = new ArrayList<>();
        for (String event : List.of(
                "{\"type\":\"response.output_item.added\",\"output_index\":0,\"item\":{\"type\":\"reasoning\",\"id\":\"rs_1\"}}",
                "{\"type\":\"response.output_text.delta\",\"output_index\":1,\"content_index\":0,\"delta\":\"hel\"}",
                "{\"type\":\"response.output_item.added\",\"output_index\":2,\"item\":{\"type\":\"function_call\",\"id\":\"fc_1\",\"call_id\":\"call_1\",\"name\":\"weather\",\"arguments\":\"\"}}",
                "{\"type\":\"response.function_call_arguments.delta\",\"output_index\":2,\"delta\":\"{\"}",
                "{\"type\":\"response.function_call_arguments.delta\",\"output_index\":2,\"delta\":\"}\"}",
                "{\"type\":\"response.function_call_arguments.done\",\"output_index\":2,\"arguments\":\"{}\"}",
                "{\"type\":\"response.output_text.done\",\"output_index\":1,\"content_index\":0,\"text\":\"hello\"}"))
            assertFalse(mapper.accept(json.readTree(event), events::add));
        assertTrue(mapper.accept(json.readTree("{\"type\":\"response.completed\",\"response\":" + OpenAiResponsesTest.RESPONSE + "}"), events::add));
        assertEquals("hello", events.stream().filter(StreamEvent.TextDelta.class::isInstance)
                .map(StreamEvent.TextDelta.class::cast).map(StreamEvent.TextDelta::text).reduce("", String::concat));
        var calls = events.stream().filter(StreamEvent.ToolCallDelta.class::isInstance)
                .map(StreamEvent.ToolCallDelta.class::cast).flatMap(e -> e.toolCalls().stream()).toList();
        assertEquals("call_1", calls.getFirst().id());
        assertTrue(calls.stream().allMatch(c -> c.index() == 0));
        assertEquals("{}", calls.stream().map(c -> c.function().arguments()).reduce("", String::concat));
        assertEquals(FinishReason.TOOL_CALLS, ((StreamEvent.Finished)events.get(events.size() - 2)).reason());
        var usage = (StreamEvent.UsageReported)events.getLast();
        assertEquals(3, usage.cachedTokens());
        assertEquals(2, usage.reasoningTokens());
    }

    @Test void mapsIncompleteAndRejectsFailedOrErrorEvents() {
        var response = (tools.jackson.databind.node.ObjectNode)json.readTree(OpenAiResponsesTest.RESPONSE);
        response.put("status", "incomplete").putObject("incomplete_details").put("reason", "max_output_tokens");
        List<StreamEvent> events = new ArrayList<>();
        assertTrue(new OpenAiResponsesStreamMapper().accept(json.valueToTree(Map.of("type", "response.incomplete", "response", response)), events::add));
        assertTrue(events.stream().anyMatch(e -> e instanceof StreamEvent.Finished f && f.reason() == FinishReason.LENGTH));
        for (String type : List.of("response.failed", "error")) {
            assertThrows(UpstreamProtocolException.class, () -> new OpenAiResponsesStreamMapper()
                    .accept(json.valueToTree(Map.of("type", type)), e -> {}));
        }
    }

    @Test void streamsResponsesThroughPublicEndpointAndRejectsTruncation() throws Exception {
        for (boolean truncated : List.of(false, true)) {
            var builder = RestClient.builder().baseUrl("https://openai.test");
            var server = MockRestServiceServer.bindTo(builder).build();
            var adapter = new OpenAiAdapter(new OpenAiMapper(Map.of()), new OpenAiStreamMapper(), json, builder.build(),
                    Map.of("test", Set.of(ApiType.RESPONSES)));
            String upstream = "event: response.output_text.delta\r\ndata: {\"type\":\"response.output_text.delta\",\"output_index\":1,\"content_index\":0,\"delta\":\"hello\"}\r\n\r\n";
            if (!truncated) upstream += "data: {\"type\":\"response.completed\",\"response\":" + OpenAiResponsesTest.RESPONSE.replace("\n", "") + "}";
            server.expect(requestTo("https://openai.test/responses")).andRespond(withSuccess(upstream, MediaType.TEXT_EVENT_STREAM));
            var mvc = MockMvcBuilders.standaloneSetup(new ResponsesController(new ProviderRegistry(List.of(adapter))))
                    .setControllerAdvice(new ApiExceptionHandler()).build();
            var pending = mvc.perform(post("/v1/responses").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"model\":\"openai/test\",\"input\":\"hi\",\"stream\":true}"))
                    .andExpect(request().asyncStarted()).andReturn();
            pending.getAsyncResult(5000);
            String body = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(body.contains(truncated ? "event:response.failed" : "event:response.completed"));
            assertFalse(body.contains("[DONE]"));
            if (truncated) assertFalse(body.contains("event:response.completed"));
            server.verify();
        }
    }

    @Test void interleavesParallelCallsWithIndependentIndexes() {
        var mapper = new OpenAiResponsesStreamMapper();
        List<StreamEvent> events = new ArrayList<>();
        for (int i : List.of(3, 7)) {
            mapper.accept(json.valueToTree(Map.of("type", "response.output_item.added", "output_index", i,
                    "item", Map.of("type", "function_call", "id", "fc_" + i, "call_id", "call_" + i,
                            "name", "weather", "arguments", ""))), events::add);
        }
        for (int i : List.of(7, 3, 7, 3)) mapper.accept(json.valueToTree(Map.of("type", "response.function_call_arguments.delta",
                "output_index", i, "delta", "x")), events::add);
        var calls = events.stream().filter(StreamEvent.ToolCallDelta.class::isInstance)
                .map(StreamEvent.ToolCallDelta.class::cast).flatMap(e -> e.toolCalls().stream()).toList();
        assertEquals(List.of(0, 1, 1, 0, 1, 0), calls.stream().map(StreamEvent.ToolCall::index).toList());
        assertEquals("call_3", calls.get(0).id());
        assertEquals("call_7", calls.get(1).id());
    }

    @Test void preservesRefusalAndRejectsInconsistentFinalSnapshots() {
        var mapper = new OpenAiResponsesStreamMapper();
        List<StreamEvent> events = new ArrayList<>();
        mapper.accept(json.readTree("""
                {"type":"response.refusal.delta","output_index":0,"content_index":0,"delta":"Cannot"}
                """), events::add);
        mapper.accept(json.readTree("""
                {"type":"response.refusal.done","output_index":0,"content_index":0,"refusal":"Cannot help"}
                """), events::add);
        assertEquals("Cannot help", events.stream().filter(StreamEvent.RefusalDelta.class::isInstance)
                .map(StreamEvent.RefusalDelta.class::cast).map(StreamEvent.RefusalDelta::refusal).reduce("", String::concat));
        assertThrows(UpstreamProtocolException.class, () -> mapper.accept(json.readTree("""
                {"type":"response.refusal.done","output_index":0,"content_index":0,"refusal":"different"}
                """), events::add));
    }
}
