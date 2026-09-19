# LLM Router 코드 리뷰

- 리뷰 날짜: 2026-09-12
- 범위: 전체 애플리케이션 소스, 설정, 기존 테스트, 프로젝트 설계 문서
- 기준: OpenAI 호환 요청 처리, 프로바이더 매핑, 스트리밍 및 오류 처리
- 상태: 리뷰 당시 발견한 문제를 기록한 문서이며, 수정 완료 여부를 의미하지 않습니다.

## 전체 평가

API DTO, 내부 모델, 프로바이더별 DTO를 분리하고 매퍼와 HTTP 호출의 책임을 나눈 구조는 유지하기 좋습니다. Anthropic의 상태를 갖는 스트림 매퍼를 요청마다 생성하는 점도 적절합니다.

현재 우선순위는 구조 개편보다 클라이언트 호환성과 오류 처리 보완입니다. 아래 다섯 가지는 정상적인 요청을 실패시키거나, 요청 옵션과 실패 정보를 유실할 수 있습니다.

P1은 우선 수정할 기능 문제, P2는 후속 수정할 동작 및 오류 처리 문제를 뜻합니다.

## 1. [P1] 표준 스트리밍 요청이 일반 응답으로 처리됨

**관련 코드**

- [ChatCompletionsController.java](../src/main/java/com/limecoding/llmrouter/api/ChatCompletionsController.java): 리뷰 당시 30~40행
- [OpenAiChatRequest.java](../src/main/java/com/limecoding/llmrouter/api/dto/chat/OpenAiChatRequest.java)

### 문제와 영향

`POST /v1/chat/completions`는 항상 동기 `chat()`을 호출합니다. 스트리밍은 별도의 `/v1/chat/stream`으로 분리되어 있으며, 수신 DTO에는 `stream` 필드가 없습니다.

따라서 표준 엔드포인트에 `stream: true`를 보내도 SSE 응답으로 분기하지 않습니다. OpenAI SDK에서 주소만 바꿔 스트리밍을 사용하는 방식과 호환되지 않습니다.

### 재현 결과

다음 JSON을 수신 DTO로 역직렬화한 뒤, 외부 호출을 하지 않는 가짜 어댑터를 연결해 컨트롤러를 호출했습니다.

```json
{
  "model": "openai/test",
  "messages": [{ "role": "user", "content": "hi" }],
  "stream": true
}
```

반환 객체의 `object` 값은 일반 응답인 `chat.completion`이었습니다. 이 검증은 HTTP 서버를 통한 통합 테스트가 아닌 DTO와 컨트롤러의 로컬 호출입니다.

### 권장 수정

- 수신 DTO에 `stream`을 추가합니다.
- `/v1/chat/completions`에서 해당 값에 따라 일반 JSON 응답과 SSE 응답을 선택합니다.
- 같은 엔드포인트에 대해 `stream` 생략, `false`, `true`의 응답 형식을 테스트합니다.

참고: [OpenAI Chat API 규격](https://developers.openai.com/api/reference/resources/chat)

## 2. [P1] system·developer 메시지 처리 실패

**관련 코드**

- [ChatCompletionsController.java](../src/main/java/com/limecoding/llmrouter/api/ChatCompletionsController.java): 리뷰 당시 110행
- [Role.java](../src/main/java/com/limecoding/llmrouter/core/model/Role.java)

### 문제와 영향

내부 `Role`에는 `USER`, `ASSISTANT`만 정의되어 있습니다. 컨트롤러는 입력 역할을 대문자로 바꾼 뒤 바로 `Role.valueOf()`를 호출합니다.

따라서 `system` 또는 `developer` 지시문이 포함된 요청은 프로바이더 호출 전에 실패합니다.

### 재현 결과

```text
system: IllegalArgumentException No enum constant ...Role.SYSTEM
developer: IllegalArgumentException No enum constant ...Role.DEVELOPER
```

### 권장 수정

- 지원할 역할과 시스템 지시문을 내부 모델에 표현합니다.
- 프로바이더별 요청 규격에 맞게 변환합니다. enum에 값만 추가하고 모든 프로바이더에 그대로 보내는 것으로 끝내지 않습니다.
- 지원하지 않는 역할은 명확한 클라이언트 오류로 반환합니다.
- 프로바이더별로 지시문과 일반 대화가 함께 있는 요청을 테스트합니다.

참고: [OpenAI Chat 메시지 역할 규격](https://developers.openai.com/api/reference/resources/chat)

## 3. [P1] Anthropic 스트림 오류가 정상 종료처럼 전달될 수 있음

**관련 코드**

- [AnthropicEvent.java](../src/main/java/com/limecoding/llmrouter/provider/anthropic/wire/chunk/AnthropicEvent.java)
- [AnthropicStreamMapper.java](../src/main/java/com/limecoding/llmrouter/provider/anthropic/AnthropicStreamMapper.java): 리뷰 당시 34~36행
- [AnthropicAdapter.java](../src/main/java/com/limecoding/llmrouter/provider/anthropic/AnthropicAdapter.java): 리뷰 당시 69~80행
- [ChatCompletionsController.java](../src/main/java/com/limecoding/llmrouter/api/ChatCompletionsController.java): 리뷰 당시 60~61행

### 문제와 영향

Anthropic은 HTTP 연결이 성공한 뒤에도 SSE 안에 `error` 이벤트를 보낼 수 있습니다. 현재 이벤트 모델은 이를 명시적으로 정의하지 않아 `Unknown`으로 읽고, 스트림 매퍼는 로그만 남긴 뒤 빈 목록을 반환합니다.

이후 업스트림이 연결을 종료하면 어댑터가 정상 반환하고 컨트롤러는 `[DONE]`을 전송합니다. 클라이언트가 오류로 잘린 응답을 정상 완료로 취급할 수 있습니다.

### 재현 결과

다음 공식 오류 형식의 JSON을 실제 이벤트 DTO로 역직렬화하고 매퍼에 전달했습니다.

```json
{
  "type": "error",
  "error": {
    "type": "overloaded_error",
    "message": "Overloaded"
  }
}
```

```text
Unknown[type=error] -> []
```

오류 이벤트 유실은 로컬 실행으로 확인했고, 그 뒤 `[DONE]`이 전송되는 경로는 코드 검토로 확인했습니다.

### 권장 수정

- `error` 이벤트를 명시적으로 모델링하고 실패로 전파합니다.
- 정상 종료 이벤트 수신 여부를 추적해, 단순 EOF와 정상 완료를 구분합니다.
- 이미 SSE 전송을 시작한 경우의 오류 전달 형식을 정합니다.
- 정상 완료, 중간 오류 이벤트, 정상 종료 이벤트 없는 EOF를 각각 테스트합니다.

참고: [Anthropic 스트리밍 이벤트 및 오류 규격](https://platform.claude.com/docs/en/build-with-claude/streaming)

## 4. [P2] temperature가 업스트림 요청에서 누락됨

**관련 코드**

- [OpenAiMapper.java](../src/main/java/com/limecoding/llmrouter/provider/openai/OpenAiMapper.java): 리뷰 당시 19행 및 31행
- [OpenAiRequest.java](../src/main/java/com/limecoding/llmrouter/provider/openai/wire/OpenAiRequest.java)
- [AnthropicMapper.java](../src/main/java/com/limecoding/llmrouter/provider/anthropic/AnthropicMapper.java): 리뷰 당시 19행 및 28행
- [AnthropicRequest.java](../src/main/java/com/limecoding/llmrouter/provider/anthropic/wire/AnthropicRequest.java)

### 문제와 영향

API DTO와 내부 `ChatRequest`는 `temperature`를 받지만, 두 프로바이더의 송신 DTO에는 해당 필드가 없습니다. 따라서 일반 응답과 스트리밍 모두에서 사용자가 지정한 값이 유실됩니다.

### 재현 결과

내부 요청에 `temperature = 0.0`을 지정한 뒤 두 매퍼의 결과를 JSON으로 직렬화했습니다. 두 결과 모두 다음과 같았으며 `temperature`가 없었습니다.

```json
{
  "model": "test",
  "messages": [{ "role": "user", "content": "hi" }],
  "max_tokens": 10,
  "stream": false
}
```

### 권장 수정

- 송신 DTO와 매핑에 `temperature`를 추가합니다.
- 모델별 지원 여부와 허용 범위를 고려해 전달하거나 명시적으로 거절합니다.
- `null`과 명시적인 `0.0`을 구분하고, 일반 요청과 스트리밍 요청 모두에서 직렬화 결과를 테스트합니다.

## 5. [P2] 입력 오류가 서버 오류로 이어짐

**관련 코드**

- [ChatCompletionsController.java](../src/main/java/com/limecoding/llmrouter/api/ChatCompletionsController.java): 리뷰 당시 73행
- [ModelRef.java](../src/main/java/com/limecoding/llmrouter/core/model/ModelRef.java)
- [ProviderRegistry.java](../src/main/java/com/limecoding/llmrouter/config/ProviderRegistry.java): 리뷰 당시 27행

### 문제와 영향

입력 검증과 공통 예외 처리가 없어 아래 오류가 컨트롤러 밖으로 전파됩니다.

| 입력 | 현재 코드의 동작 |
| --- | --- |
| `messages` 누락 | `request.messages().stream()`에서 `NullPointerException` |
| `model` 누락 또는 잘못된 형식 | `ModelRef.parse()`에서 `IllegalArgumentException` |
| 미등록 프로바이더 | `ProviderRegistry.get()`에서 일반 `RuntimeException` |

이 예외들을 클라이언트 오류로 매핑하는 핸들러가 없어 서버 오류로 이어집니다. 클라이언트는 입력을 고쳐야 하는 상황과 서버 장애를 구분하기 어렵습니다.

`messages` 누락에 따른 `NullPointerException`은 로컬 호출로 재현했습니다. HTTP 상태 코드는 서버를 띄워 검증하지 않았으며, 예외 처리 코드 부재를 바탕으로 판단했습니다.

### 권장 수정

- 필수 필드, 메시지 목록, 역할, 토큰 수 등 입력을 검증합니다.
- 잘못된 모델 형식과 미등록 프로바이더에 구체적인 예외를 사용합니다.
- 공통 예외 처리에서 입력 오류를 HTTP 400과 일관된 오류 본문으로 변환합니다.
- 입력 오류와 업스트림 장애를 구분하고, HTTP 계층 테스트로 상태 코드와 본문을 확인합니다.

## 검증 범위와 한계

- IntelliJ MCP를 통한 프로젝트 빌드는 성공했고, 빌드 문제 목록은 비어 있었습니다.
- 임시 Java 프로그램에서 가짜 어댑터, 실제 DTO, 컨트롤러 및 매퍼를 사용해 주요 사례를 재현했습니다.
- 검증 프로그램은 저장소 밖 임시 디렉터리에 작성했으며, 프로젝트 테스트에 추가한 회귀 테스트는 아닙니다.
- 외부 OpenAI·Anthropic API 호출과 HTTP 서버를 통한 종단 간 검증은 수행하지 않았습니다.
- 리뷰 과정에서 애플리케이션 소스는 수정하지 않았습니다.

## 테스트 보완 우선순위

리뷰 당시 기존 테스트는 다음 두 개입니다.

- [LlmRouterApplicationTests.java](../src/test/java/com/limecoding/llmrouter/LlmRouterApplicationTests.java): Spring 컨텍스트 로딩
- [OpenAiAdapterTest.java](../src/test/java/com/limecoding/llmrouter/provider/openai/OpenAiAdapterTest.java): 실제 OpenAI API 호출

이 테스트들만으로는 위의 매핑 및 스트리밍 문제를 검출하기 어렵습니다. 다음 순서로 외부 API 없이 실행 가능한 테스트를 보완하는 것이 좋습니다.

1. 컨트롤러의 일반 응답·스트리밍 분기와 잘못된 입력의 HTTP 응답
2. 프로바이더별 역할·옵션 매핑 및 송신 JSON
3. SSE 정상 완료·오류 이벤트·중간 EOF 처리
4. 실제 API 호출 테스트와 기본 회귀 테스트의 실행 경로 분리
