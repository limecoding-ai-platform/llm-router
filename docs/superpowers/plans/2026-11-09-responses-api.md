# Responses API Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** POST /v1/responses accepts text and function inputs and returns JSON or Responses SSE.
**Architecture:** API-only adaptation to ChatRequest, ChatResponse and StreamEvent; existing providers remain reusable.
**Tech Stack:** Java 25, Spring MVC, Jackson 3, JUnit 5, MockMvc.
**Spec:** ../specs/2026-11-09-responses-api-design.md (approved)

## Global Constraints
- Preserve api → core ← provider; no external API calls in tests.
- Unsupported capabilities return HTTP 400; no response persistence.
- Work in the current issue branch; do not merge or publish.

## Review Focus
- Reject malformed/unsupported nested input instead of dropping data.
- Preserve call_id across function calls and outputs, including parallel calls.
- Emit accumulated final output after usage, not immediately at finish_reason.
- Emit incomplete or failed terminal events instead of false success.
- Stable output indexes and item IDs during fragmented function streams.

## Task 1: Input and output mapping
Files: api/responses/ResponsesMapper.java, api/responses/ResponsesResponse.java; tests api/ResponsesMapperTest.java.
Interfaces: toDomain(JsonNode request, String model): ChatRequest; response(ChatResponse chat, String model): ResponsesResponse.
- [x] Add failing tests for text, instructions, message arrays, tools, function results, invalid fields and unsupported features.
- [x] Run Gradle tests (observed missing implementation), implement mapping and repeat (passed).

## Task 2: HTTP and streaming
Files: api/ResponsesController.java, api/responses/ResponsesStream.java; tests api/ResponsesControllerTest.java, api/ResponsesStreamTest.java.
Interfaces: ResponsesStream(String model, Consumer<Map<String,Object>> sink); accept(StreamEvent), complete(), fail().
- [x] Test JSON routing, 400 errors, named SSE with sequence_number, delta accumulation, parallel calls, usage, incomplete and failure.
- [x] Implement controller and per-request stream state; run tests until passing.

## Task 3: Documentation and regression verification
Files: README.md, docs/responses-api.md.
- [x] Document supported subset, examples, stateless behavior and limitations.
- [x] Run full Gradle tests, inspect diff and request independent review; fix important findings.
- [x] Record test evidence and leave changes available in the current branch.

## Execution ledger
- Ruling: User approved execution; continue inline without another plan approval.
- Ruling: Use existing issue branch and leave changes uncommitted for review.
- Ruling: Supporting instructions/temperature requires SYSTEM role and provider wire mappings; extend these narrowly while retaining existing routing.
- Task 1: complete — initial compile failure for missing mapper observed, then full suite green.
- Task 2: complete — initial compile failure for missing controller/stream observed, then full suite green.
- Review: identified output_text logprobs replay and tool-first assistant output replay failures. Added regression tests, observed both fail, then fixed input normalization. Refusal output replay is also covered.
- Review follow-up: original findings resolved. Added a failing provider-wire test for refusal-only history, then preserved refusal text in Anthropic message content.
- Task 3: complete — `gradlew.bat test --console=plain` BUILD SUCCESSFUL; 70 tests, 0 failures, 0 errors. `git diff --check` clean. No live API calls. Changes left uncommitted on the issue branch.
