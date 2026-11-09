# Responses API

`POST /v1/responses`는 기존 공통 모델과 provider 라우팅을 사용하는 **stateless Responses 호환 부분 규격**입니다. OpenAI의 전체 Responses API를 프록시하지 않습니다. 수신 DTO/변환은 `api/responses`, HTTP 진입점은 `ResponsesController`에 있습니다.

## 텍스트 요청

`model`은 기존과 동일하게 `provider/model`이며 설정에 등록한 모델을 사용합니다.

```http
POST /v1/responses
Content-Type: application/json

{
  "model": "openai/<configured-model>",
  "instructions": "한국어로 간단히 답하세요.",
  "input": "안녕!",
  "max_output_tokens": 256,
  "store": false
}
```

응답은 `object: "response"`, `resp_` ID, `created_at`, `status`, `model`, `output`, `usage`, `error`, `incomplete_details`, `store: false`를 포함합니다. 텍스트는 `output`의 `message.content` 내 `output_text.text`로 전달됩니다. 토큰 사용량은 `input_tokens`, `output_tokens`, `total_tokens`입니다.

`input`은 문자열 또는 메시지/함수 항목 배열입니다. 메시지의 `content`에는 문자열이나 `input_text` 배열을 사용할 수 있고, assistant 이력에는 `output_text`와 `refusal`도 사용할 수 있습니다. 반환한 텍스트 항목의 빈 `annotations`/`logprobs`는 재입력할 수 있습니다. `instructions` 및 대화 시작 부분의 `system`/`developer` 메시지는 공통 SYSTEM 역할로 전달됩니다. Anthropic에서는 최상위 `system`으로 합쳐집니다.

선택 옵션은 `instructions`, `max_output_tokens`(양의 정수), `temperature`(0–2), `tools`, `tool_choice`, `stream`, `store: false`입니다. 실제 모델/provider의 옵션 지원 범위는 별도로 적용됩니다.

## 함수 도구 호출

```json
{
  "model": "anthropic/<configured-model>",
  "input": "서울 날씨를 알려줘",
  "tools": [{
    "type": "function",
    "name": "get_weather",
    "description": "도시 날씨 조회",
    "parameters": {
      "type": "object",
      "properties": {"city": {"type": "string"}},
      "required": ["city"]
    }
  }],
  "tool_choice": "auto"
}
```

`tool_choice`는 `auto`, `none`, `required`, 또는 `{"type":"function","name":"get_weather"}`입니다. 도구 호출은 `output`의 `function_call` 항목으로 반환되며 `id`, `call_id`, `name`, JSON 문자열인 `arguments`를 포함합니다.

도구는 클라이언트가 실행합니다. 다음 요청의 `input`에 이전 대화, 응답의 `output` 항목, 실행 결과를 함께 넣습니다. 병렬 호출은 각 호출의 `call_id`에 해당하는 결과를 모두 포함해야 합니다.

```json
[
  {"role":"user","content":"서울 날씨를 알려줘"},
  {"type":"function_call","call_id":"call_123","name":"get_weather","arguments":"{\"city\":\"Seoul\"}"},
  {"type":"function_call_output","call_id":"call_123","output":"맑음, 20도"}
]
```

## 스트리밍

`stream: true`이면 `text/event-stream`으로 전송합니다. 각 SSE의 `event`와 JSON `type`은 동일하며, `sequence_number`는 0부터 증가합니다.

- 시작: `response.created`, `response.in_progress`
- 출력: `response.output_item.added`, 메시지의 `response.content_part.added`
- 텍스트: `response.output_text.delta` 및 `.done`
- 거절: `response.refusal.delta` 및 `.done`
- 함수 인자: `response.function_call_arguments.delta` 및 `.done`
- 항목 종료: `response.content_part.done`, `response.output_item.done`
- 최종: `response.completed`, `response.incomplete`, 또는 `response.failed`

최종 이벤트의 `response`에는 누적 출력과 provider가 보고한 사용량이 들어갑니다. 길이 제한 또는 content filter 종료는 `incomplete_details.reason`으로 구분합니다. 비정상 중단은 `response.failed`이며 내부 오류 내용은 노출하지 않습니다. Chat Completions의 `[DONE]` 마커는 사용하지 않습니다.

## 지원 범위

응답을 서버에 저장하지 않으며 `store`를 생략해도 false입니다. `store: true`, `previous_response_id`, `conversation`, `background`, 이미지/파일/오디오 입력, 내장 도구, structured output, `reasoning` 및 나머지 미지원 필드는 400 오류입니다. 지원하지 않는 데이터를 조용히 무시하지 않습니다.

현재 OpenAI 송신은 기존 `/chat/completions`, Anthropic 송신은 기존 Messages API를 사용합니다. 이 엔드포인트 추가만으로 이슈 #5의 `reasoning_effort` 오류가 해결되는 것은 아닙니다.

실제 외부 API 호출 없이 매핑, HTTP 응답, SSE 순서/누적/실패 및 기존 테스트를 검증합니다.

참고: [OpenAI Responses 마이그레이션 안내](https://developers.openai.com/api/docs/guides/migrate-to-responses)
