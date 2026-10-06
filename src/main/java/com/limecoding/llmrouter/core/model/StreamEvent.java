package com.limecoding.llmrouter.core.model;

import java.util.List;

public sealed interface StreamEvent {
    record RoleDelta(int choiceIndex, String role) implements StreamEvent {
    }

    record TextDelta(int choiceIndex, String text) implements StreamEvent {
        public TextDelta(String text) {
            this(0, text);
        }
    }

    record RefusalDelta(int choiceIndex, String refusal) implements StreamEvent {
    }

    record ToolCallDelta(
            int choiceIndex,
            List<ToolCall> toolCalls
    ) implements StreamEvent {
    }

    record ToolCall(
            int index,
            String id,
            String type,
            FunctionDelta function
    ) {
    }

    record FunctionDelta(
            String name,
            String arguments
    ) {
    }

    record Finished(int choiceIndex, FinishReason reason) implements StreamEvent {
        public Finished(FinishReason reason) {
            this(0, reason);
        }
    }

    record UsageReported(Usage usage, Integer cachedTokens, Integer reasoningTokens) implements StreamEvent {
        public UsageReported(Usage usage) {
            this(usage, null, null);
        }
    }
}
