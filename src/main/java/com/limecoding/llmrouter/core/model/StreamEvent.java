package com.limecoding.llmrouter.core.model;

public sealed interface StreamEvent {
    record TextDelta(String text) implements StreamEvent {
    }

    record Finished(FinishReason reason) implements StreamEvent {
    }

    record UsageReported(Usage usage) implements StreamEvent {
    }
}
