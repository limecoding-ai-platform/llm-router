# OpenAI Responses Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline with TDD.

**Goal:** Route OpenAI requests to the caller's API, enforcing per-model support.
**Architecture:** ApiType travels in ChatRequest. OpenAiAdapter dispatches to separate wire mappers and enforces supported-apis before I/O.
**Tech Stack:** Java 25, Spring MVC/RestClient, Jackson 3, JUnit, local JDK HTTP server.
**Spec:** ../specs/2026-11-09-openai-responses-design.md

## Global Constraints
- Preserve existing Chat Completions and Anthropic behavior.
- No live OpenAI calls; no automatic fallback between APIs.
- Reject unsupported paths before SSE headers.

## Review Focus
- Responses-only models must not require Chat Completions token configuration.
- Both routes on one model must actually target different upstream URLs.
- Function call_id must not be confused with output item id.
- Completed/incomplete/failed and truncated streams must be distinguished.
- Parallel function indexes must be independent of reasoning/message output indexes.

## Tasks
- [x] Add failing tests for ApiType, configuration, request/response mapping and endpoint selection.
- [x] Implement core/config support and Responses wire mapping; verify tests.
- [x] Add SSE and HTTP integration tests; implement per-request stateful decoder and adapter dispatch.
- [x] Document support configuration, review changes independently, fix findings, run full suite.

## Ledger
- User explicitly approved capability-set plus ingress-based routing; proceed without another permission gate.
- Keep work in existing issue branch, uncommitted until requested.
- Initial RED: missing ApiType/mapper/capabilities constructor. Subsequent test fixture failure was missing Chat Completions primitive fields; corrected fixture to match documented response.
- Independent review found assistant history used output-only content types without output identity. Regression test observed RED, then changed reconstructed history to easy input_text parts including refusal text.
- Verification: full Gradle suite passes 84 tests, 0 failures/errors. Includes loopback HTTP JSON/SSE, authorization/path/body checks and HTTP 429 without fallback. git diff --check clean. No live OpenAI call.

## Follow-up: second-turn failure
- User reported OpenWebUI second-turn failure with gpt-5.6-terra. Live minimal probe confirmed assistant input_text array returns 400 invalid_value at input[1].content[0]; same history with plain-string content returns 200 completed.
- Earlier SDK-schema-based assumption and corresponding regression assertion were wrong. Replace reconstructed assistant content with a plain string, preserving refusal text.
- Added a two-turn HTTP regression using the first response output as the second request history; both new expectations failed before the fix.
- After the fix: full Gradle suite passes 85 tests, 0 failures/errors; git diff --check clean. Live verification covered minimal synthetic messages, not the user's OpenWebUI deployment.
