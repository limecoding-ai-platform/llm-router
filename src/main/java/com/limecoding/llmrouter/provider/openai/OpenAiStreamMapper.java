package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.core.model.FinishReason;
import com.limecoding.llmrouter.core.model.StreamEvent;
import com.limecoding.llmrouter.core.model.Usage;
import com.limecoding.llmrouter.provider.openai.wire.OpenAiChunk;

import java.util.ArrayList;
import java.util.List;

import static com.limecoding.llmrouter.core.model.StreamEvent.*;

public class OpenAiStreamMapper {

    public List<StreamEvent> toEvents(OpenAiChunk chunk) {
        var events = new ArrayList<StreamEvent>();

        for (var choice : chunk.choices() == null ? List.<OpenAiChunk.Choice>of() : chunk.choices()) {
            var delta = choice.delta();

            if (delta != null) {
                if (delta.role() != null) {
                    events.add(new RoleDelta(choice.index(), delta.role()));
                }
                if (delta.content() != null) {
                    events.add(new TextDelta(choice.index(), delta.content()));
                }
                if (delta.refusal() != null) {
                    events.add(new RefusalDelta(choice.index(), delta.refusal()));
                }

                if (delta.toolCalls() != null && !delta.toolCalls().isEmpty()) {
                    var toolCalls = delta.toolCalls().stream()
                            .map(call -> {
                                var function = call.function();

                                return new StreamEvent.ToolCall(
                                        call.index(),
                                        call.id(),
                                        call.type(),
                                        function == null
                                                ? null
                                                : new StreamEvent.FunctionDelta(
                                                function.name(),
                                                function.arguments()
                                        )
                                );
                            })
                            .toList();

                    events.add(new ToolCallDelta(
                            choice.index(),
                            toolCalls
                    ));
                }
            }

            if (choice.finishReason() != null) {
                events.add(new Finished(choice.index(), toFinishReason(choice.finishReason())));
            }
        }

        if (chunk.usage() != null) {
            var usage = chunk.usage();
            events.add(new UsageReported(toUsage(usage),
                    usage.promptTokensDetails() == null ? null : usage.promptTokensDetails().cachedTokens(),
                    usage.completionTokensDetails() == null ? null : usage.completionTokensDetails().reasoningTokens()));
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
