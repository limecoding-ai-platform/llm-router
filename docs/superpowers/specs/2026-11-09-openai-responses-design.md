# OpenAI Responses 송신 지원

사용자가 승인한 방식: 모델별 `supported-apis: [chat_completions, responses]`로 지원 여부를 표현하고 실제 송신 API는 수신 경로와 일치시킨다. 한 API로 강제하거나 실패 시 다른 API로 자동 재시도하지 않는다.

## 구조와 범위
- 공통 ChatRequest에 ApiType을 추가한다. 기존 생성자는 CHAT_COMPLETIONS를 기본으로 하여 호환성을 유지한다.
- OpenAI 모델 설정에 지원 API 집합을 추가한다. 생략 시 기존 CHAT_COMPLETIONS만 지원한다. Responses 전용 모델에는 max-tokens-param이 필요하지 않다.
- ProviderAdapter의 사전 검증 훅으로 미지원 경로를 SSE 시작 전 400으로 반환한다. 어댑터 직접 호출도 검증한다.
- OpenAI 송신 전용 Responses 매퍼와 스트림 변환기를 추가한다. api 수신 DTO와 공유하지 않는다.
- 텍스트, system 지시, 함수 정의/선택, 함수 호출 이력 및 결과, 사용량, 거절, incomplete/failed 상태를 처리한다. 저장은 false이다.
- 기존 Anthropic 수신 형식 변환은 유지한다. OpenAI의 reasoning 설정·서버 상태 저장·내장 도구·멀티모달은 이번 범위에 포함하지 않는다.

## 대안
모델별 고정 API 선택은 양쪽 API 지원을 활용할 수 없어 제외했다. 수신 경로 기반 선택과 별도의 지원 목록을 사용한다.

## 검증
매핑·설정 바인딩·HTTP 경로·SSE 이벤트 변환 테스트와 로컬 HTTP 서버 기반 송신 테스트를 추가한다. 미지원 모델/API가 실제 호출 없이 거절되는지, 실패/잘린 스트림이 성공으로 처리되지 않는지 확인한다. 기존 전체 테스트를 실행한다.

상태: 구현 완료. 전체 84개 테스트 통과, 로컬 HTTP 서버 검증 및 독립 리뷰 수행. 실제 OpenAI 계정 호출은 수행하지 않음.
