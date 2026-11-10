# LLM Router 문서

## 제품 기획

- [내부 팀용 통합 콘솔 기획·설계](superpowers/specs/2026-11-10-router-console-design.md): 제품 목표, 첫 버전 범위, 권한, 요청 기록, 예산, 알림 및 미확정 정책

## API와 구조

- [Responses API](responses-api.md)
- [모델 라우팅 규약](model-routing.md)
- [프로젝트 구조](project-structure.md)

## 기존 설계와 검토 기록

- [Responses 수신 API 설계](superpowers/specs/2026-11-09-responses-api-design.md)
- [OpenAI Responses 송신 설계](superpowers/specs/2026-11-09-openai-responses-design.md)
- [코드 리뷰](code-review.md): 과거 검토 기록으로, 현재의 미해결 문제 목록을 의미하지 않음

제품 기획 문서의 **확정 요구사항**, **설계 제안**, **미확정 사항**을 구분해서 읽습니다. 기획에 포함된 기능이 현재 구현되어 있다는 의미는 아닙니다.


## 로컬 PostgreSQL 실행

Docker Compose를 사용할 수 있는 환경이 필요합니다.

1. `.env.local.example`을 `.env.local`로 복사하고 DB 접속 정보를 설정합니다.
   `.env.local`은 Git에 커밋하지 않습니다.

2. PostgreSQL을 실행합니다.

   ```bash
   docker compose --env-file .env.local -f compose.local.yaml up -d
   ```

3. 실행 상태를 확인합니다.

   ```bash
   docker compose --env-file .env.local -f compose.local.yaml ps
   ```

   상태가 `healthy`이면 DB가 연결을 받을 준비가 된 것입니다.

### 종료

```bash
docker compose --env-file .env.local -f compose.local.yaml down
```

데이터는 Docker 볼륨에 유지됩니다. `down -v`를 실행하면 DB 데이터도 삭제됩니다.

### 참고

- 로컬 접속 주소: `localhost:5432`
- DB 이름·사용자·비밀번호: `.env.local`에 설정한 값
- 초기화된 DB의 계정·비밀번호는 환경 변수만 변경해도 갱신되지 않습니다.