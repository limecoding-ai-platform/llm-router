package com.limecoding.llmrouter.provider.anthropic;

import com.limecoding.llmrouter.core.model.FinishReason;
import com.limecoding.llmrouter.core.model.StreamEvent;
import com.limecoding.llmrouter.core.model.Usage;
import com.limecoding.llmrouter.provider.anthropic.wire.chunk.AnthropicEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.limecoding.llmrouter.core.model.StreamEvent.*;
import static com.limecoding.llmrouter.provider.anthropic.wire.chunk.AnthropicEvent.*;

public class AnthropicStreamMapper {
    private Integer inputTokens;
    private static final Logger log = LoggerFactory.getLogger(AnthropicStreamMapper.class);

    public List<StreamEvent> toEvents(AnthropicEvent chunk) {
        return switch (chunk) {
            case MessageStart(var message) -> {
                this.inputTokens = message.usage().inputTokens();
                yield List.of();
            }
            case ContentBlockDelta(var i, var delta) -> "text_delta".equals(delta.type())
                    ? List.of(new TextDelta(delta.text()))
                    : List.of();
            case MessageDelta(var meta, var usage) -> List.of(new Finished(toFinishReason(meta.stopReason())),
                    new UsageReported(toUsage(inputTokens, usage.outputTokens())));
            case ContentBlockStart cbs -> List.of();
            case ContentBlockStop cbs -> List.of();
            case MessageStop ms -> List.of();
            case Ping p -> List.of();
            case Unknown(var type) -> {
                log.debug("unhandled anthropic event: {}", type);
                yield List.of();
            }
        };
    }

    private FinishReason toFinishReason(String reason) {
        return switch (reason) {
            case "max_tokens", "model_context_window_exceeded" -> FinishReason.LENGTH;
            case "tool_use" -> FinishReason.TOOL_CALLS;
            case "refusal" -> FinishReason.CONTENT_FILTER;
            case null, default -> FinishReason.STOP;
        };
    }

    private Usage toUsage(int inputTokens, int outputTokens) {
        return new Usage(inputTokens, outputTokens);
    }
}
