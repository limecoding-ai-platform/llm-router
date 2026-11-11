package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.core.model.*;
import com.limecoding.llmrouter.api.ResponsesController;
import com.limecoding.llmrouter.core.ProviderRegistry;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP boundary with a local upstream stand-in: no OpenAI credentials or paid calls. */
class OpenAiResponsesHttpTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void sendsJsonAndSseOverHttpWithAuthorizationAndExactPath() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
        server.createContext("/v1/responses", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestHeaders().getFirst("Content-Type"), body));
            boolean streaming = json.readTree(body).path("stream").asBoolean();
            String response = streaming
                    ? "event: response.completed\ndata: " + json.writeValueAsString(Map.of("type", "response.completed",
                        "response", json.readTree(OpenAiResponsesTest.RESPONSE))) + "\n\n"
                    : OpenAiResponsesTest.RESPONSE;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", streaming ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(200, streaming ? 0 : bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var adapter = adapter(server);
            for (boolean streaming : List.of(false, true)) {
                var request = new ChatRequest("test", List.of(Message.user("안녕")), null, null, 40, null, streaming, ApiType.RESPONSES);
                if (streaming) {
                    List<StreamEvent> events = new ArrayList<>();
                    adapter.stream(request, events::add);
                    assertTrue(events.stream().anyMatch(e -> e instanceof StreamEvent.TextDelta t && t.text().equals("hello")));
                    assertInstanceOf(StreamEvent.UsageReported.class, events.getLast());
                } else assertEquals("hello", adapter.chat(request).message().content());
            }
            assertEquals(2, requests.size());
            for (var request : requests) {
                assertEquals("POST", request.method());
                assertEquals("/v1/responses", request.path());
                assertEquals("Bearer local-test", request.auth());
                assertTrue(request.contentType().startsWith("application/json"));
                var body = json.readTree(request.body());
                assertEquals("test", body.path("model").asText());
                assertEquals("안녕", body.at("/input/0/content").asText());
                assertFalse(body.path("store").asBoolean());
                assertEquals(40, body.path("max_output_tokens").asInt());
            }
        } finally { server.stop(0); }
    }

    @Test void preservesRateLimitStatusForBothModesWithoutFallback() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> paths = new CopyOnWriteArrayList<>();
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = "{\"error\":{\"message\":\"rate limited\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var adapter = adapter(server);
            for (boolean streaming : List.of(false, true)) {
                var request = new ChatRequest("test", List.of(Message.user("hi")), null, null, null, null, streaming, ApiType.RESPONSES);
                var error = assertThrows(RestClientResponseException.class, () -> {
                    if (streaming) adapter.stream(request, e -> {}); else adapter.chat(request);
                });
                assertEquals(429, error.getStatusCode().value());
                assertTrue(error.getResponseBodyAsString().contains("rate limited"));
            }
            assertEquals(List.of("/v1/responses", "/v1/responses"), paths);
        } finally { server.stop(0); }
    }

    private OpenAiAdapter adapter(HttpServer server) {
        return new OpenAiAdapter(new OpenAiMapper(Map.of()), new OpenAiStreamMapper(), json,
                RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                        .defaultHeader("Authorization", "Bearer local-test").build(), Map.of("test", Set.of(ApiType.RESPONSES)));
    }

    @Test void secondConversationTurnSendsReplayableAssistantHistory() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<tools.jackson.databind.JsonNode> requests = new CopyOnWriteArrayList<>();
        server.createContext("/v1/responses", exchange -> {
            var request = json.readTree(exchange.getRequestBody().readAllBytes());
            requests.add(request);
            // Contract verified against live gpt-5.6-terra: assistant input_text parts return 400.
            boolean invalidAssistant = false;
            for (var message : request.path("input")) {
                if ("assistant".equals(message.path("role").asText()) && !message.path("content").isString()) invalidAssistant = true;
            }
            String response = invalidAssistant
                    ? "{\"error\":{\"code\":\"invalid_value\",\"param\":\"input[1].content[0]\",\"message\":\"Invalid value: 'input_text'. Supported values are: 'output_text' and 'refusal'.\"}}"
                    : "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_test\",\"model\":\"test\",\"status\":\"completed\","
                        + "\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"OK\"}]}],"
                        + "\"usage\":{\"input_tokens\":3,\"output_tokens\":1}}}\n\n";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", invalidAssistant ? "application/json" : "text/event-stream");
            exchange.sendResponseHeaders(invalidAssistant ? 400 : 200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var mvc = MockMvcBuilders.standaloneSetup(new ResponsesController(new ProviderRegistry(List.of(adapter(server))))).build();
            var input = json.createArrayNode();
            input.addObject().put("role", "user").put("content", "첫 번째 질문");
            for (int turn = 0; turn < 2; turn++) {
                var body = json.createObjectNode().put("model", "openai/test").put("stream", true);
                body.set("input", input);
                var pending = mvc.perform(post("/v1/responses").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                        .andExpect(request().asyncStarted()).andReturn();
                pending.getAsyncResult(5000);
                String sse = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                assertFalse(sse.contains("event:response.failed"), "Turn " + (turn + 1) + " failed: " + sse);
                var completed = sse.lines().filter(line -> line.startsWith("data:"))
                        .map(line -> json.readTree(line.substring(5))).filter(event -> "response.completed".equals(event.path("type").asText()))
                        .findFirst().orElseThrow();
                if (turn == 0) {
                    for (var item : completed.at("/response/output")) input.add(item);
                    input.addObject().put("role", "user").put("content", "두 번째 질문");
                }
            }
            assertEquals(2, requests.size());
            assertEquals("assistant", requests.get(1).at("/input/1/role").asText());
            assertEquals("OK", requests.get(1).at("/input/1/content").asText());
            assertEquals("두 번째 질문", requests.get(1).at("/input/2/content").asText());
        } finally { server.stop(0); }
    }
    private record RecordedRequest(String method, String path, String auth, String contentType, String body) {}
}
