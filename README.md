# llm-router
외부 API를 중앙에서 관리하기 위한 Router입니다.

- `POST /v1/chat/completions`: Chat Completions 호환 API
- `POST /v1/responses`: 텍스트, 함수 도구 호출, SSE를 지원하는 [Responses 호환 API](docs/responses-api.md)

## 문서

### 제품 기획

- [내부 팀용 통합 콘솔 기획·설계](docs/superpowers/specs/2026-11-10-router-console-design.md): 제품 목표, 첫 버전 범위, 권한, 요청 기록, 예산, 알림 및 미확정 정책

### API와 구조

- [Responses API](docs/responses-api.md)
- [모델 라우팅 규약](docs/model-routing.md)
- [프로젝트 구조](docs/project-structure.md)

### 기존 설계와 검토 기록

- [Responses 수신 API 설계](docs/superpowers/specs/2026-11-09-responses-api-design.md)
- [OpenAI Responses 송신 설계](docs/superpowers/specs/2026-11-09-openai-responses-design.md)
- [코드 리뷰](docs/code-review.md): 과거 검토 기록으로, 현재의 미해결 문제 목록을 의미하지 않음

제품 기획 문서의 **확정 요구사항**, **설계 제안**, **미확정 사항**을 구분해서 읽습니다. 기획에 포함된 기능이 현재 구현되어 있다는 의미는 아닙니다.


## 로컬 PostgreSQL 실행

Docker Compose를 사용할 수 있는 환경이 필요합니다.

1. `.env.local.example`을 `.env.local`로 복사하고 DB 접속 정보를 설정합니다.
   `.env.local`은 Git에 커밋하지 않습니다.

2. PostgreSQL을 실행합니다.

   ```bash
   docker compose --env-file .env.local -f compose.local.yaml up -d --wait
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

## Spring Boot DB 연결 및 실행

애플리케이션은 다음 환경 변수를 사용합니다. `.env.local`은 Spring Boot가 자동으로 읽지 않으므로 CLI에서 환경 변수로 불러온 뒤 실행합니다.

| 변수 | 용도 |
|---|---|
| `DB_NAME` | PostgreSQL 초기 DB 이름 및 URL에서 참조할 이름 |
| `DB_USERNAME` | PostgreSQL 초기 사용자 및 애플리케이션 접속 사용자 |
| `DB_PASSWORD` | PostgreSQL 초기 비밀번호 및 애플리케이션 접속 비밀번호 |
| `DB_URL` | JDBC 접속 주소. 로컬에서는 `jdbc:postgresql://localhost:5432/${DB_NAME}` |

Router 실행에는 `ROUTER_API_KEY`, `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`도 필요합니다. 실제 제공업체 호출 시 유효한 키를 사용합니다.

Java 25를 준비하고, 위 절차로 PostgreSQL을 실행한 뒤 프로젝트 루트에서 다음 명령을 실행합니다.

**Windows PowerShell**

아래 명령은 예시 파일처럼 따옴표 없이 작성한 `KEY=value` 형식을 읽습니다. `${DB_NAME}` 참조는 Spring이 해석합니다.

```powershell
foreach ($line in Get-Content -LiteralPath .env.local) {
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
.\gradlew.bat bootRun
```

**Linux / macOS / WSL (Bash)**

직접 작성한 `.env.local`을 셸에서 읽습니다. 값에 공백이나 셸 특수문자가 있으면 Bash 문법에 맞게 인용합니다.

```bash
set -a
source ./.env.local
set +a
./gradlew bootRun
```

환경 변수는 현재 터미널에 적용됩니다. 새 터미널에서는 다시 불러와야 합니다. Docker Compose의 `--env-file`은 별도로 실행하는 Spring Boot나 Gradle에 환경 변수를 전달하지 않습니다.

테스트는 동일한 터미널에서 `./gradlew test`(Windows: `.\gradlew.bat test`)로 실행합니다. 현재 컨텍스트 시작 테스트도 실행 중인 PostgreSQL과 위 DB 변수가 필요합니다. 테스트용 Router·제공업체 키는 더미 값을 사용할 수 있습니다.

CI는 테스트용 PostgreSQL 서비스 컨테이너와 환경 변수를 제공해 빌드·테스트를 실행합니다. 운영 배포의 PostgreSQL 제공 및 환경 변수 주입은 별도로 구성해야 합니다. 현재 테스트는 DB 없이 실행되지 않습니다.

`password authentication failed`가 발생하면 최초 DB 생성 시 비밀번호와 실행 설정을 비교합니다. `database "{DB_NAME}" does not exist`는 URL의 변수 표기가 `${DB_NAME}`인지 확인합니다. 애플리케이션도 Compose 안에서 실행하는 경우 호스트는 `localhost` 대신 서비스명 `postgres`를 사용합니다.

## DB 마이그레이션

Flyway는 애플리케이션 시작 시 아직 적용하지 않은 마이그레이션을 실행합니다. DB 연결에는 `spring.datasource` 설정을 사용합니다.

SQL 파일은 Git으로 변경 과정을 관리하고, Flyway는 각 DB에 적용한 이력을 관리합니다. 마이그레이션은 당시의 데이터를 보관하는 백업이나 스냅샷을 대신하지 않습니다.

### 파일 위치와 이름

- 위치: `src/main/resources/db/migration`
- 형식: `V버전__설명.sql`
- 예: `V1__create_users.sql`
- 버전과 설명 사이에는 밑줄 두 개를 사용합니다.

### 작성 원칙

- 버전은 중복되지 않도록 순서대로 증가시킵니다.
- 공유 환경에 적용한 파일은 수정하거나 삭제하지 않습니다.
- 스키마 수정은 새로운 버전의 파일로 추가합니다.
- 데이터 삭제 등 파괴적인 변경은 영향과 복구 방법을 PR에 명시합니다.
- 새 DB에서의 전체 적용과 기존 DB에서의 추가 적용을 확인합니다.

Git 커밋을 되돌려도 DB는 자동으로 되돌아가지 않습니다. 적용한 변경을 취소하려면 취소하는 SQL을 새 버전으로 작성합니다. 삭제된 데이터는 반대 SQL만으로 복구되지 않으므로 별도의 백업이 필요합니다.

### 적용 확인

`flyway_schema_history` 테이블에서 버전과 적용 결과(`success`)를 확인합니다. 애플리케이션을 재시작해도 이미 적용한 버전은 다시 실행되지 않습니다.
