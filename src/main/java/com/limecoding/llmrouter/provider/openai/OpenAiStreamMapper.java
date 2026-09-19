package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.core.model.FinishReason;
import com.limecoding.llmrouter.core.model.StreamEvent;
import com.limecoding.llmrouter.core.model.Usage;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.limecoding.llmrouter.core.model.StreamEvent.*;

public class OpenAiStreamMapper {

    public List<StreamEvent> toEvents(OpenAiChunk chunk) {
        var events = new ArrayList<StreamEvent>(2);

        if (chunk.usage() != null) {
            events.add(new UsageReported(toUsage(chunk.usage())));
        }
        if (chunk.choices() == null || chunk.choices().isEmpty()) {
            return events;
        }

        var choice = chunk.choices().getFirst();
        var delta = choice.delta();

        if (delta != null && delta.content() != null && !delta.content().isEmpty()) {
            events.add(new TextDelta(delta.content()));
        }
        if (choice.finishReason() != null) {
            events.add(new Finished(toFinishReason(choice.finishReason())));
        }
        return events;
    }

    private Usage toUsage(OpenAiChunk.Usage wire) {
        return new Usage(wire.promptTokens(), wire.completionTokens());
    }

    private FinishReason toFinishReason(String finishReason) {
        return switch (finishReason) {
            case "length" -> FinishReason.LENGTH;
            case "content_filter" -> FinishReason.CONTENT_FILTER;
            case "tool_calls" -> FinishReason.TOOL_CALLS;
            case null, default -> FinishReason.STOP;
        };
    }
}