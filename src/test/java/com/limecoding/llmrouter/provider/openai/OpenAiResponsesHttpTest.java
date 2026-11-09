package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.core.model.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

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
    private record RecordedRequest(String method, String path, String auth, String contentType, String body) {}
}
