package com.limecoding.llmrouter.core.model;

import tools.jackson.databind.JsonNode;

public record Tool(
        String type,
        Function function
) {

    /**
     * 클라이언트 요청의 {@code tools[].function}에 대응하는 함수 정의.
     *
     * <p>요청의 도구 정의 예시 (전체 요청 중 {@code tools} 부분):
     * <pre>{@code
     * {
     *   "tools": [{
     *     "type": "function",
     *     "function": {
     *       "name": "get_weather",
     *       "description": "도시의 날씨 조회",
     *       "parameters": {
     *         "type": "object",
     *         "properties": {
     *           "location": {"type": "string"},
     *           "unit": {"type": "string", "enum": ["celsius", "fahrenheit"]}
     *         },
     *         "required": ["location"]
     *       }
     *     }
     *   }]
     * }
     * }</pre>
     *
     * <p>{@code parameters}에는 위 예시의 {@code parameters} 객체만 들어간다.
     * 바깥의 {@code type: "function"}은 도구 종류이고,
     * 안쪽의 {@code type: "object"}는 입력 인자 전체의 자료형이다.
     * 두 값을 서로 대입하면 안 된다.
     * {@code location}, {@code unit}은 도구 작성자가 정한 인자 이름이며,
     * {@code type}, {@code properties}, {@code enum}, {@code required}는 JSON Schema 키워드다.
     *
     * <p>실제 호출 인자 {@code {"location": "Seoul", "unit": "celsius"}}는
     * 도구 호출의 {@code arguments}에 들어가며, 이 필드에는 들어가지 않는다.
     * Anthropic 요청으로 변환할 때는 이 스키마 객체를 {@code input_schema}에 매핑한다.
     *
     * @param name 함수 이름
     * @param description 함수의 용도와 사용 조건
     * @param parameters 입력 인자의 구조와 제약을 정의하는 JSON Schema 객체
     * @param strict 엄격한 스키마 준수 설정
     */
    public record Function(
            String name,
            String description,
            JsonNode parameters,
            Boolean strict
    ) {
    }
}


