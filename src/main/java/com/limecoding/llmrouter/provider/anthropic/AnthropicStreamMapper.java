package com.limecoding.llmrouter.provider.anthropic;

import com.limecoding.llmrouter.core.model.FinishReason;
import com.limecoding.llmrouter.core.model.StreamEvent;
import com.limecoding.llmrouter.core.model.Usage;
import com.limecoding.llmrouter.provider.anthropic.wire.chunk.AnthropicEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static com.limecoding.llmrouter.core.model.StreamEvent.*;
import static com.limecoding.llmrouter.provider.anthropic.wire.chunk.AnthropicEvent.*;

public class AnthropicStreamMapper {
    private Integer inputTokens;
    // 요청마다 새 인스턴스를 사용한다. Anthropic 블록 번호는 텍스트도 세지만 도구 번호는 도구만 센다.
    private final Map<Integer, Integer> toolIndices = new HashMap<>();
    private int nextToolIndex;
    private static final Logger log = LoggerFactory.getLogger(AnthropicStreamMapper.class);

    public List<StreamEvent> toEvents(AnthropicEvent chunk) {
        return switch (chunk) {
            case MessageStart(var message) -> {
                this.inputTokens = message.usage().inputTokens();
                yield List.of();
            }
            case ContentBlockDelta(var i, var delta) -> toDeltaEvents(i, delta);
            case MessageDelta(var meta, var usage) -> List.of(new Finished(toFinishReason(meta.stopReason())),
                    new UsageReported(toUsage(inputTokens, usage.outputTokens())));
            case ContentBlockStart(var i, var block) -> {
                if (!"tool_use".equals(block.type())) yield List.of();
                int toolIndex = nextToolIndex++;
                toolIndices.put(i, toolIndex);
                yield List.of(new ToolCallDelta(0, List.of(new ToolCall(
                        toolIndex, block.id(), "function", new FunctionDelta(block.name(), "")))));
            }
            case ContentBlockStop(var i) -> {
                toolIndices.remove(i);
                yield List.of();
            }
            case MessageStop ms -> List.of();
            case Ping p -> List.of();
            case Unknown(var type) -> {
                log.debug("unhandled anthropic event: {}", type);
                yield List.of();
            }
        };
    }

    private List<StreamEvent> toDeltaEvents(int blockIndex, Delta delta) {
        if ("text_delta".equals(delta.type())) return List.of(new TextDelta(delta.text()));
        if (!"input_json_delta".equals(delta.type())) return List.of();

        Integer toolIndex = toolIndices.get(blockIndex);
        if (toolIndex == null) {
            throw new IllegalArgumentException("No active tool_use block for index: " + blockIndex);
        }
        // ID와 이름은 시작 이벤트에서만 전달한다. 인자 조각은 그대로 이어 붙일 수 있어야 한다.
        return List.of(new ToolCallDelta(0, List.of(new ToolCall(
                toolIndex, null, null, new FunctionDelta(null, delta.partialJson())))));
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
