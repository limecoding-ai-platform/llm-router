# 로컬 개발 환경 실행

DB → 백엔드 → 프런트엔드 순서로 실행합니다. 아래 명령은 별도 안내가 없으면 저장소 루트에서 실행합니다.
백엔드와 프런트엔드는 각각 별도 터미널에서 실행 상태를 유지합니다.

## 준비 사항

- Docker Compose를 사용할 수 있는 Docker 환경
- JDK 25 (`JAVA_HOME`과 `java -version` 확인)
- Node.js 24.14.0, npm 11 (기준 버전 11.9.0)

Gradle은 저장소의 Wrapper를 사용하므로 별도 설치하지 않습니다.

## 1. 환경 파일 준비

최초 실행 시 예시 파일을 복사합니다. 이미 파일이 있으면 기존 값을 유지하세요.

**Windows PowerShell**

```powershell
Copy-Item .env.local.example .env.local
Copy-Item frontend/.env.example frontend/.env.local
```

**Bash (Linux / macOS / WSL)**

```bash
cp .env.local.example .env.local
cp frontend/.env.example frontend/.env.local
```

루트 `.env.local`에서 다음 값을 설정합니다.

| 변수 | 로컬 설정 |
| --- | --- |
| `DB_NAME`, `DB_USERNAME` | 예시의 `llm_router` 사용 가능 |
| `DB_PASSWORD` | 로컬 DB 비밀번호 지정 |
| `DB_URL` | 예시의 `jdbc:postgresql://localhost:5432/${DB_NAME}` 유지 |
| `ROUTER_API_KEY` | 직접 정한 비어 있지 않은 로컬 전용 키 |
| `OPENAI_API_KEY`, `ANTHROPIC_API_KEY` | 모델 목록 조회만 확인한다면 `local-placeholder` 같은 더미 값 |

제공업체 키는 시작 시 빈 값이 허용되지 않습니다. 모델 목록 조회는 외부 LLM을 호출하지 않지만,
실제 생성 요청에는 해당 제공업체의 유효한 키가 필요합니다.

`frontend/.env.local`은 기본값을 유지합니다.

```dotenv
VITE_API_BASE_URL=/api
BACKEND_URL=http://localhost:8080
```

루트 환경 파일은 DB·백엔드용, `frontend/` 환경 파일은 Vite용입니다.
실제 환경 파일은 Git에서 제외됩니다. `VITE_*`에는 API 키나 DB 비밀번호를 넣지 않습니다.

## 2. PostgreSQL 실행

```sh
docker compose --env-file .env.local -f compose.local.yaml up -d --wait
docker compose --env-file .env.local -f compose.local.yaml ps
```

PostgreSQL은 `localhost:5432`에서 실행되며 상태가 `healthy`인지 확인합니다.

## 3. 백엔드 실행 — 터미널 A

Spring Boot는 `.env.local`을 자동으로 읽지 않습니다. 같은 터미널에서 환경 변수를 불러온 뒤 실행합니다.

**Windows PowerShell**

예시 파일처럼 따옴표 없는 `KEY=value` 형식으로 작성합니다. `${DB_NAME}`은 Spring이 해석합니다.

```powershell
foreach ($envLine in Get-Content -LiteralPath .env.local) {
    if ($envLine -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
.\backend\gradlew.bat -p backend bootRun
```

**Bash**

직접 작성한 환경 파일을 읽습니다. 공백이나 셸 특수문자가 들어간 값은 Bash 문법에 맞게 인용하세요.

```bash
set -a
source ./.env.local
set +a
./backend/gradlew -p backend bootRun
```

시작 로그에서 애플리케이션 시작 완료와 포트 `8080`을 확인합니다.
환경 값을 바꾸면 다시 불러오고 백엔드를 재시작합니다.

## 4. 프런트엔드 실행 — 터미널 B

```sh
cd frontend
npm ci
npm run dev
```

`npm ci`는 최초 설치 또는 lockfile 변경 후 실행합니다.
터미널에 표시된 주소로 접속합니다. 기본값은 `http://localhost:5173`이며 포트가 사용 중이면 다른 포트를 선택합니다.
프런트엔드 환경 변수 변경 후에는 Vite를 재시작합니다.

화면만 개발할 때는 DB와 백엔드 없이 이 단계만 실행해도 됩니다. 이 경우 실제 API 연결 확인은 실패합니다.

## 5. 화면에서 연결 확인

1. 홈의 **백엔드 연결 확인**에 루트 `.env.local`의 `ROUTER_API_KEY`와 같은 값을 입력합니다.
2. **연결 확인**을 눌러 연결 성공과 모델 목록을 확인합니다. 모델 수는 백엔드 설정에 따라 달라집니다.
3. 잘못된 키로 요청하면 인증 오류가 표시됩니다. 올바른 키로 수정하고 다시 시도할 수 있습니다.
4. 키와 결과는 초기화·페이지 이동·새로고침 시 제거됩니다.

요청 경로는 `브라우저 /api/v1/models → Vite 프록시 → 백엔드 /v1/models`입니다.
이 단계는 모델 카탈로그 조회만 수행합니다. 현재 입력하는 Router 키는 콘솔 로그인 계정이 아닙니다.
홈 외 관리 페이지는 준비 중 화면이며, 존재하지 않는 경로는 404 화면을 표시합니다.

## 검사 및 종료

프런트엔드 검사는 `frontend/`에서 실행합니다. 실제 백엔드와 DB 없이 실행할 수 있습니다.

```sh
npm run check
```

린트·포맷·테스트·타입 검사·빌드를 수행합니다.
백엔드 테스트는 DB를 실행하고 환경 변수를 불러온 터미널에서 실행합니다.

```powershell
# Windows, 저장소 루트
.\backend\gradlew.bat -p backend test
```

```bash
# Bash, 저장소 루트
./backend/gradlew -p backend test
```

백엔드와 프런트엔드는 각 터미널에서 `Ctrl+C`로 종료합니다. DB는 저장소 루트에서 종료합니다.

```sh
docker compose --env-file .env.local -f compose.local.yaml down
```

DB 데이터는 볼륨에 유지됩니다. `down -v`는 데이터를 삭제하므로 일반 종료에 사용하지 않습니다.

## 문제 해결

| 증상 | 확인할 항목 |
| --- | --- |
| `npm ci`에서 엔진 버전 오류 | `node -v`, `npm -v`와 위 준비 버전 확인 |
| DB 실행 실패 | Docker 실행 상태, 5432 포트 충돌, `.env.local`의 DB 필수 값 확인 |
| DB 인증 실패 | 기존 볼륨 생성 시 사용한 비밀번호 확인. 환경 변수 변경만으로 DB 비밀번호가 바뀌지 않음 |
| 백엔드 환경 변수 누락·키 검증 오류 | 실행 터미널에서 환경 파일을 불러왔는지, 세 API 키가 빈 값인지 확인 |
| 8080 포트 충돌 | 기존 백엔드 실행 여부 확인. 다른 포트로 실행하면 `BACKEND_URL`도 맞추고 Vite 재시작 |
| 홈에서 인증 오류(401) | 화면의 키와 실행 중인 백엔드의 `ROUTER_API_KEY` 일치 여부 확인 |
| 연결 실패·502·타임아웃 | 백엔드 시작 로그, 8080 접근 가능 여부, `BACKEND_URL` 확인 |
| 모델 응답 형식 오류 | `VITE_API_BASE_URL=/api`와 프록시 대상이 올바른지 확인 |
| Flyway 이력과 파일 불일치 | 현재 브랜치의 마이그레이션 파일과 DB 적용 이력 확인. 데이터 삭제나 이력 수정 전에 원인을 확인 |

세부 설정은 [백엔드 안내](../backend/README.md)와 [프런트엔드 안내](../frontend/README.md)를 참고하세요.
운영 배포와 운영 프록시 설정은 이 로컬 실행 절차의 범위에 포함하지 않습니다.
