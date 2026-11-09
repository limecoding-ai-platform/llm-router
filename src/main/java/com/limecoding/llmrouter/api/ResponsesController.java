package com.limecoding.llmrouter.api;

import com.limecoding.llmrouter.api.dto.error.OpenAiErrorResponse;
import com.limecoding.llmrouter.api.responses.ResponsesMapper;
import com.limecoding.llmrouter.api.responses.ResponsesStream;
import com.limecoding.llmrouter.core.ProviderAdapter;
import com.limecoding.llmrouter.core.ProviderRegistry;
import com.limecoding.llmrouter.core.model.ChatRequest;
import com.limecoding.llmrouter.core.model.ModelRef;
import com.limecoding.llmrouter.core.model.UnKnownProviderException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/v1")
public class ResponsesController {
    private final ProviderRegistry registry;
    private final ResponsesMapper mapper = new ResponsesMapper();

    public ResponsesController(ProviderRegistry registry) { this.registry = registry; }

    @PostMapping("/responses")
    public ResponseEntity<?> create(@RequestBody JsonNode request) {
        ModelRef model = ModelRef.parse(ResponsesMapper.text(request, "model"));
        ChatRequest domain = mapper.toDomain(request, model.model());
        ProviderAdapter adapter = registry.get(model.provider());
        adapter.validateRequest(domain);
        if (!domain.includeUsage()) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.response(adapter.chat(domain), model.original()));
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).cacheControl(CacheControl.noCache())
                .body(stream(adapter, domain, model.original()));
    }

    private SseEmitter stream(ProviderAdapter adapter, ChatRequest domain, String model) {
        var emitter = new SseEmitter(0L);
        var disconnected = new AtomicBoolean();
        Thread worker = Thread.ofVirtual().unstarted(() -> {
            ResponsesStream state = null;
            try {
                state = new ResponsesStream(model, event -> {
                    if (disconnected.get()) throw new IllegalStateException("Client disconnected");
                    try {
                        emitter.send(SseEmitter.event().name((String) event.get("type")).data(event, MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        disconnected.set(true);
                        throw new UncheckedIOException(e);
                    }
                });
                adapter.stream(domain, state::accept);
                state.complete();
                emitter.complete();
            } catch (Exception error) {
                if (disconnected.get()) return;
                try {
                    if (state != null) state.fail();
                    emitter.complete();
                } catch (Exception sendError) { emitter.completeWithError(sendError); }
            }
        });
        emitter.onCompletion(() -> { disconnected.set(true); worker.interrupt(); });
        emitter.onTimeout(() -> { disconnected.set(true); worker.interrupt(); emitter.complete(); });
        emitter.onError(error -> { disconnected.set(true); worker.interrupt(); });
        worker.start();
        return emitter;
    }

    @ExceptionHandler({IllegalArgumentException.class, UnKnownProviderException.class})
    public ResponseEntity<OpenAiErrorResponse> invalidRequest(RuntimeException error) {
        return ResponseEntity.badRequest().body(OpenAiErrorResponse.of(error.getMessage(),
                "invalid_request_error", null, "invalid_request"));
    }
}
