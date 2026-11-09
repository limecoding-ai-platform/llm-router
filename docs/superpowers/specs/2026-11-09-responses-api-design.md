# Responses 수신 API 추가 설계

## 목적
클라이언트가 POST /v1/responses로 요청하고 Responses 형식의 JSON 또는 SSE 응답을 받을 수 있게 한다. 현재 model의 provider 접두사 라우팅과 공통 ChatRequest/ChatResponse/StreamEvent를 재사용한다.

## 접근 방식
- 채택 방향: api 계층에 Responses DTO, 순수 매퍼, 컨트롤러, SSE 변환기를 추가한다. 사용자가 확인한 수신 엔드포인트 추가 범위이며 저장소의 api → core ← provider 구조에 맞는다.
- 대안: OpenAI Responses API로 직접 전달한다. OpenAI 전용 기능을 보존하기 쉽지만 Anthropic과 공통 라우팅을 재사용하기 어렵고 송신 구현 변경까지 필요하다.

## 제안하는 지원 범위
- model, 문자열 또는 메시지 목록 input, instructions, max_output_tokens, temperature.
- 함수 tools, tool_choice, function_call, function_call_output으로 도구 호출과 후속 결과 입력을 지원한다.
- 응답의 object=response, id, created_at, status, model, output, usage를 구성한다. 메시지는 output_text, 도구 호출은 function_call 항목으로 변환한다.
- stream=true는 Responses의 이름 있는 SSE 이벤트로 생성, 항목/내용 추가, 텍스트/함수 인자 delta, 완료를 전달한다. 최종 응답에 누적 출력과 사용량을 포함하고 중도 실패를 성공 완료로 표시하지 않는다.
- 기존 모델로 표현할 수 없는 이미지, 파일, 내장 도구, 응답 저장/조회, previous_response_id 등은 명시적인 400 오류로 처리한다. 저장은 하지 않는다.
- reasoning 설정은 현재 공통 모델에 없으므로 조용히 무시하지 않고 미지원으로 처리한다. 이 변경 자체가 이슈 #5의 reasoning_effort 오류 해결을 의미하지는 않는다.

## 구현 및 검증
입력 검증과 매핑은 HTTP I/O와 분리한다. 테스트는 텍스트, 다중 턴, 도구 호출/결과 연결, 잘못된 입력, SSE 순서 및 누적 결과, 길이 제한 종료와 실패 경로를 검증한다. 기존 Chat Completions 회귀 테스트도 실행한다. provider의 실제 외부 API 호출 없이 검증한다.

## 참고
https://developers.openai.com/api/docs/guides/migrate-to-responses

## 상태
사용자 승인 완료. 구현 및 검증 완료. 전체 테스트 70개 통과; 실제 외부 API 호출은 검증 범위에 포함하지 않음.
