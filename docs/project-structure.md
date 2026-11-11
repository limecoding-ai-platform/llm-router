# 프로젝트 구조

LLM Router의 저장소 구조와 백엔드 패키지 경계를 정리한 문서입니다.

## 저장소 구조

- `backend/`: Spring Boot 소스·테스트, Gradle Wrapper와 빌드 설정, Dockerfile
- `frontend/`: React·TypeScript·Vite 관리 콘솔을 구성할 위치
- `.github/`: 공통 CI와 이슈 템플릿
- `docs/`: 전체 설계·API·개발 문서
- 루트 Compose와 환경 변수 예시: 공통 로컬 환경 및 배포 구성

백엔드는 `backend/`를 작업 디렉터리로 사용하며 독립적으로 빌드합니다.
Java 소스 위치는 `backend/src/main/java`, 테스트 위치는 `backend/src/test/java`입니다.
아래 단일 모듈 원칙은 백엔드 Gradle 프로젝트에 적용됩니다.
과거 설계·구현 계획·검토 기록의 경로와 실행 명령은 작성 당시 기준입니다.

## 전제

- **v0~v1은 단일 모듈로 간다.** 멀티모듈 분리는 빌드 설정 시간만 잡아먹는다.
- 대신 **패키지 경계는 처음부터 제대로 긋는다.** 나중에 모듈로 쪼갤 때 잘라내기만 하면 되도록.
- 기술 스택: Spring Boot 4.1.0 / Java 25 / Spring MVC

---

## 패키지 구조

```
com.limecoding.llmrouter
├─ api/                      ← 수신부: OpenAI 호환 스펙 구현
│   ├─ ChatCompletionsController
│   └─ dto/                  ← OpenAI 와이어 포맷 DTO
│       ├─ OpenAiChatRequest
│       └─ OpenAiChatResponse
│
├─ core/                     ← 프로바이더도 HTTP도 모르는 순수 영역
│   ├─ model/
│   │   ├─ ChatRequest / ChatResponse
│   │   ├─ Message / Role
│   │   ├─ FinishReason      (enum)
│   │   └─ Usage
│   ├─ ProviderAdapter       (인터페이스)
│   └─ ModelRouter           (model 문자열 → 어댑터 선택)
│
├─ provider/                 ← 송신부: 프로바이더별 구현
│   ├─ openai/
│   │   ├─ OpenAiAdapter
│   │   ├─ OpenAiRequestMapper
│   │   └─ wire/             ← 업스트림 전용 DTO
│   └─ anthropic/
│       ├─ AnthropicAdapter
│       ├─ AnthropicRequestMapper
│       └─ wire/
│
└─ config/
```

## 의존 방향

```
   api  ──▶  core  ◀──  provider
             (api와 provider는 서로를 모른다)
```

`api`와 `provider`는 **둘 다 `core`만 알고, 서로는 모릅니다.** `core`는 아무것도 모릅니다.

---

## 이 도메인의 특수한 함정

### 1. `api/dto`와 `provider/openai/wire`를 절대 공유하지 말 것

라우터 특유의 함정입니다. 수신 포맷도 OpenAI, OpenAI 어댑터의 송신 포맷도 OpenAI라서 *"같은 클래스 쓰면 되잖아"* 가 됩니다. 실제로 v0에서는 거의 동일합니다.

그런데 이 둘은 **독립적으로 변합니다.** 우리가 지원하기로 한 인터페이스와 OpenAI가 실제로 받는 스펙은 다른 물건입니다.

- OpenAI가 `max_completion_tokens`로 넘어가도 → 우리 수신 계약은 `max_tokens`를 계속 받아줘야 한다
- 우리가 `provider` 같은 커스텀 필드를 추가해도 → 업스트림에 새면 안 된다

파일 두 벌이 낭비처럼 보이지만, 붙여놓으면 v2에서 vLLM이 "OpenAI 호환인데 미묘하게 다를 때" 손댈 곳이 없어집니다.

### 2. `core`가 OpenAI 모양이 되지 않게 할 것

어차피 OpenAI 포맷으로 뱉으니까 `ChatResponse`에 `choices` 배열을 넣고 싶어집니다. **넣지 마세요.** `choices`는 OpenAI의 `n>1` 설계에서 나온 것이지 도메인 개념이 아닙니다.

내부 모델은 이래야 합니다:

```java
record ChatResponse(
    String id, String model, Message message,
    FinishReason finishReason, Usage usage
) {}
```

`choices` 래핑은 **`api/dto`에서만** 일어납니다. 이 규율을 지키면 나중에 Anthropic 네이티브 포맷 엔드포인트(`/v1/messages`)를 추가할 때 `api` 계층만 하나 더 붙이면 됩니다.

### 3. 매퍼는 순수 함수로, 어댑터는 I/O만

```java
class AnthropicAdapter implements ProviderAdapter {
    public ChatResponse chat(ChatRequest req) {
        var wire = mapper.toWire(req);      // 순수
        var res  = http.post(wire);         // I/O
        return mapper.toDomain(res);        // 순수
    }
}
```

매퍼가 순수하면 **HTTP 없이 테스트가 가능합니다.** raw 응답 덤프를 픽스처로 넣고 `toDomain()`만 돌리면 되니까, API 키 안 태우고 매핑 로직을 반복 검증할 수 있습니다. v1 스트리밍에 가면 이게 결정적입니다.

---

## 경계 강제: ArchUnit

경계가 무너지는 방향이 정확히 *"어댑터가 수신 DTO를 그냥 재사용"* 이라서, 테스트로 막아두는 게 값어치가 있습니다.

```java
@Test void providerDoesNotDependOnApi() {
    noClasses().that().resideInAPackage("..provider..")
        .should().dependOnClassesThat().resideInAPackage("..api..")
        .check(classes);
}
```

---

## v1을 위한 예비 자리

지금 만들 필요는 없지만, 이렇게 확장된다는 걸 알고 패키지를 그어둡니다.

| 위치 | 추가되는 것 |
|---|---|
| `core.ProviderAdapter` | `stream()` 메서드 |
| `provider/{name}/stream/` | SSE 파서, 상태 머신 |
| `core` | `StreamEvent` 시퀀스 — `TextDelta`, `ToolCallDelta`, `Done` |
| `api` | `StreamEvent` → OpenAI SSE 청크 직렬화 인코더 |

> **핵심:** `core`의 스트림 이벤트 모델은 **두 프로토콜 중 어느 쪽도 닮지 않아야** 합니다. 닮는 순간 다른 쪽 변환이 지옥이 됩니다.

---

## 멀티모듈 전환 시점

v0~v1은 단일 모듈이 맞습니다. 위의 의존 방향(`api → core ← provider`)만 ArchUnit으로 지켜지고 있으면, 필요해졌을 때 패키지를 그대로 모듈로 잘라내면 됩니다.
