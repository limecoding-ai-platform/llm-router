# 모델 라우팅 규약

요청의 `model` 필드를 `provider/model` 형태로 받는 이유와 그 처리 방식을 정리한 문서입니다.

## 규약

```jsonc
{
  "model": "openai/gpt-4o-mini",        // <provider>/<model>
  "messages": [ ... ]
}
```

`/v1/chat/completions`의 `model`은 항상 슬래시(`/`) 하나로 구분된 `provider/model` 형태여야 합니다.

| 입력                             | provider    | model                  |
|--------------------------------|-------------|------------------------|
| `openai/gpt-4o-mini`           | `openai`    | `gpt-4o-mini`          |
| `anthropic/claude-sonnet-4-5`  | `anthropic` | `claude-sonnet-4-5`    |

---

## 왜 이렇게 받나

**서로 충돌하는 두 요구를 하나의 필드로 만족시켜야 하기 때문이다.**

1. **OpenAI SDK와 호환돼야 한다.** 클라이언트가 `base_url`만 바꿔서 그대로 붙을 수 있어야 하는 게 이 라우터의 존재 이유다. OpenAI의 요청 스펙은 고정돼 있고, 거기엔 프로바이더를 지정하는 필드가 없다. 즉 **우리가 쓸 수 있는 건 스펙에 이미 있는 필드뿐이다.**
2. **라우터는 어느 프로바이더로 보낼지 알아야 한다.** `gpt-4o-mini`와 `claude-sonnet-4-5`는 인증 헤더도, 엔드포인트도, 와이어 포맷도 다르다. 이 판단이 없으면 라우팅 자체가 성립하지 않는다.

`model`은 스펙상 자유 문자열이고 클라이언트가 어차피 매 요청 채워 보내는 값이다. 여기에 프로바이더를 실어 보내면 **스펙을 건드리지 않고** 두 요구를 동시에 만족시킬 수 있다.

### 검토했다가 버린 대안

- **`provider` 필드를 따로 추가** — 초기 구현이 이랬다. 가장 직관적이지만 OpenAI SDK는 스펙에 없는 필드를 실어 보낼 방법이 없다(`extra_body`를 쓰면 되지만, 그 순간 "SDK를 그대로 쓴다"는 전제가 깨진다). 1번 요구와 정면으로 충돌해서 폐기했다.
- **모델명만 보고 추론** (`gpt-*` → openai, `claude-*` → anthropic) — SDK 호환은 되지만 프로바이더가 늘 때마다 규칙이 늘고, 같은 모델을 여러 경로로 서빙하는 경우(예: Bedrock의 Claude)를 표현할 수 없다.
- **헤더로 전달** (`X-Provider`) — SDK의 `default_headers`로 가능은 하지만 클라이언트 인스턴스 단위 설정이라, 한 클라이언트로 프로바이더를 섞어 호출하는 이 라우터의 주 용도와 맞지 않는다.

---

## 처리 흐름

파싱은 `ModelRef.parse`가 전담하고, 컨트롤러는 쪼갠 결과만 받아 쓴다.

```
"openai/gpt-4o-mini"
        │
        ▼
   ModelRef.parse            ← core/model/ModelRef
        │
        ├─ provider "openai" ──▶ ProviderRegistry.get() ──▶ OpenAiAdapter
        └─ model "gpt-4o-mini" ─▶ ChatRequest.model      ──▶ 업스트림에 그대로 전달
```

**업스트림에는 프로바이더 접두사를 뗀 순수 모델명이 나간다.** 접두사는 이 라우터 안에서만 의미가 있는 값이므로 경계 밖으로 새지 않는다.

`ModelRef`는 원본 문자열도 `original`로 함께 보관한다. 로깅·과금 집계처럼 클라이언트가 실제로 무엇을 요청했는지 그대로 남겨야 하는 곳에서 쓴다.

### 검증 규칙

`ModelRef.parse`는 아래 경우에 `IllegalArgumentException`을 던진다.

- `model`이 null이거나 공백
- 슬래시가 없음 (`gpt-4o-mini`)
- 슬래시 앞이 비었음 (`/gpt-4o-mini`)
- 슬래시 뒤가 비었음 (`openai/`)

provider는 소문자로 정규화하고(`OpenAI/gpt-4o-mini` → `openai`), model 부분은 손대지 않는다. 모델명은 업스트림이 대소문자를 구분할 수 있어서다. 첫 번째 슬래시만 구분자로 쓰기 때문에 모델명 자체에 슬래시가 들어가도(`bedrock/anthropic.claude-v2:1/foo`) 뒷부분은 그대로 보존된다.
