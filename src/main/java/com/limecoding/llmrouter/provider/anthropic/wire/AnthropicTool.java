package com.limecoding.llmrouter.provider.anthropic.wire;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;

/**
 * Anthropic에 전달할 사용자 정의 도구의 정의.
 *
 * <p>{@code inputSchema}는 실제 호출 인자가 아니라, 인자의 구조와 제약을 정의하는
 * JSON Schema이다. 예를 들어 아래 스키마는 문자열 인자 {@code location}을 필수로 요구한다.
 *
 * <pre>{@code
 * {
 *   "type": "object",
 *   "properties": {
 *     "location": {"type": "string", "description": "조회할 도시 이름"},
 *     "unit": {"type": "string", "enum": ["celsius", "fahrenheit"]}
 *   },
 *   "required": ["location"]
 * }
 * }</pre>
 *
 * <p>이 스키마에 맞는 실제 호출 인자 예시는
 * {@code {"location": "Seoul", "unit": "celsius"}}이다.
 * {@code unit}은 {@code required}에 없으므로 생략할 수 있다.
 * 다양한 JSON Schema 키워드와 중첩 구조를 보존하기 위해 {@link JsonNode}로 표현하며,
 * 이 클래스 자체에서 스키마의 유효성을 검증하지는 않는다.
 *
 * @param name 호출할 도구의 이름
 * @param description 도구의 용도와 사용 조건에 대한 설명
 * @param inputSchema 입력 인자의 JSON Schema. OpenAI의 {@code function.parameters}에 대응한다.
 * @param inputExample 입력 스키마를 만족하는 실제 인자 객체들의 예시 목록
 */
public record AnthropicTool(
        String name,
        String description,
        @JsonProperty("input_schema") JsonNode inputSchema,
        @JsonProperty("input_example") List<JsonNode> inputExample
) {
    public AnthropicTool {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(inputSchema, "inputSchema must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private JsonNode inputSchema;
        private List<JsonNode> inputExample;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(JsonNode inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder inputExample(List<JsonNode> inputExample) {
            this.inputExample = inputExample;
            return this;
        }

        public AnthropicTool build() {
            return new AnthropicTool(name, description, inputSchema, inputExample);
        }
    }

}
