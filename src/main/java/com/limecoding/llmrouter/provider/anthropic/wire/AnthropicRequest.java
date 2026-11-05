package com.limecoding.llmrouter.provider.anthropic.wire;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.limecoding.llmrouter.provider.anthropic.wire.toolchoice.AnthropicToolChoice;

import java.util.List;
import java.util.Objects;

public record AnthropicRequest(
        String model,
        @JsonProperty("max_tokens") Integer maxTokens,
        List<AnthropicTool> tools,
        @JsonProperty("tool_choice") @JsonInclude(JsonInclude.Include.NON_NULL) AnthropicToolChoice toolChoice,
        List<AnthropicMessage> messages,
        boolean stream
) {
    public AnthropicRequest {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(maxTokens, "maxTokens must not be null");
        Objects.requireNonNull(tools, "tools must not be null");
        Objects.requireNonNull(messages, "messages must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model;
        private Integer maxTokens;
        private List<AnthropicTool> tools;
        private AnthropicToolChoice toolChoice;
        private List<AnthropicMessage> messages;
        private boolean stream;


        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder toolChoice(AnthropicToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder tools(List<AnthropicTool> tools) {
            this.tools = tools;
            return this;
        }

        public Builder messages(List<AnthropicMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        public AnthropicRequest build() {
            return new AnthropicRequest(model, maxTokens, tools, toolChoice, messages, stream);
        }

    }
}

