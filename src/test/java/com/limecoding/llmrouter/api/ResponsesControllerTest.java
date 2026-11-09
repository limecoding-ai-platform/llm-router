package com.limecoding.llmrouter.api;

import com.limecoding.llmrouter.core.*;
import com.limecoding.llmrouter.core.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResponsesControllerTest {
    private ProviderAdapter adapter() {
        var adapter = mock(ProviderAdapter.class);
        when(adapter.id()).thenReturn("anthropic");
        return adapter;
    }
    private MockMvc mvc(ProviderAdapter adapter) {
        return MockMvcBuilders.standaloneSetup(new ResponsesController(new ProviderRegistry(List.of(adapter))))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }
    @Test void routesJsonAndReturnsResponsesEnvelope() throws Exception {
        var adapter = adapter();
        when(adapter.chat(any())).thenAnswer(call -> {
            ChatRequest request = call.getArgument(0);
            assertEquals("test", request.model());
            assertEquals("hello", request.messages().getFirst().content());
            return new ChatResponse("upstream", "test", Message.assistant("ok", null, null, null), FinishReason.STOP, new Usage(1, 2));
        });
        mvc(adapter).perform(post("/v1/responses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"anthropic/test\",\"input\":\"hello\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.object").value("response"))
                .andExpect(jsonPath("$.model").value("anthropic/test"))
                .andExpect(jsonPath("$.output[0].content[0].text").value("ok"));
    }
    @Test void rejectsBadRequestBeforeCallingProvider() throws Exception {
        var adapter = adapter();
        var mvc = mvc(adapter);
        for (String body : List.of("{}", "{\"model\":\"anthropic/test\",\"input\":1}",
                "{\"model\":\"missing/test\",\"input\":\"x\"}",
                "{\"model\":\"test\",\"input\":\"x\"}",
                "{\"model\":\"anthropic/test\",\"input\":\"x\",\"stream\":true,\"reasoning\":{}}")) {
            mvc.perform(post("/v1/responses").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.type").value("invalid_request_error"));
        }
        verify(adapter, never()).chat(any());
        verify(adapter, never()).stream(any(), any());
    }
    @Test void returnsNamedSseAndSanitizesStreamFailure() throws Exception {
        for (boolean failure : List.of(false, true)) {
            var adapter = adapter();
            doAnswer(call -> {
                Consumer<StreamEvent> sink = call.getArgument(1);
                sink.accept(new StreamEvent.TextDelta("hello"));
                if (failure) throw new RuntimeException("secret-provider-detail");
                sink.accept(new StreamEvent.Finished(FinishReason.STOP));
                sink.accept(new StreamEvent.UsageReported(new Usage(1, 1)));
                return null;
            }).when(adapter).stream(any(), any());
            var mvc = mvc(adapter);
            var result = mvc.perform(post("/v1/responses").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"model\":\"anthropic/test\",\"input\":\"hi\",\"stream\":true}"))
                    .andExpect(request().asyncStarted()).andReturn();
            result.getAsyncResult(5000);
            var body = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(body.contains("event:response.output_text.delta"));
            assertTrue(body.contains(failure ? "event:response.failed" : "event:response.completed"));
            assertFalse(body.contains("secret-provider-detail"));
            assertFalse(body.contains("[DONE]"));
        }
    }
}
