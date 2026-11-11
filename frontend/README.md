# Frontend

React·TypeScript·Vite 기반 LLM Router 관리 콘솔입니다.
현재는 공통 레이아웃의 시각적 프로토타입과 개발 도구가 구성되어 있습니다.
사이드바 메뉴 전환·검색·접기, 모바일 메뉴, 계정 팝오버와 시스템·라이트·다크 테마를 확인할 수 있습니다.
테마 선택은 브라우저에 저장하며, 시스템 모드는 OS 테마 변경을 따릅니다.
메뉴는 React Router의 BrowserRouter로 이동하며 URL 직접 접근·새로고침·뒤로/앞으로 이동을 지원합니다.
공통 API 요청 모듈과 개발 프록시는 구성되어 있습니다.
인증·프로젝트 선택·실제 화면의 백엔드 호출은 후속 작업입니다.

## 개발 환경

- Node.js 24.14.0 (`.nvmrc` 기준, CI에서도 동일 버전 사용)
- npm 11 (기준 버전 11.9.0)
- React 19, TypeScript 6.0, Vite 8
- ESLint: 코드 규칙과 React Hooks 검사
- Prettier: 코드 포맷

TypeScript는 typescript-eslint의 지원 범위에 맞춰 6.0 계열을 사용합니다.
`.npmrc`의 `engine-strict` 설정으로 지원 범위를 벗어난 Node.js·npm 설치를 차단합니다.
패키지 매니저는 npm을 사용하며 `package-lock.json`을 함께 커밋합니다.

## 설치 및 실행

저장소 루트에서 실행합니다.

```bash
cd frontend
npm ci
npm run dev
```

터미널에 표시된 로컬 주소로 접속합니다. 기본 주소는 `http://localhost:5173`이며,
해당 포트가 사용 중이면 Vite가 다른 포트를 선택합니다.
현재 초기 화면은 백엔드·DB·환경 변수 없이 실행할 수 있습니다.

## 명령

아래 명령은 모두 `frontend/`에서 실행합니다.

| 명령                   | 용도                                 |
| ---------------------- | ------------------------------------ |
| `npm run dev`          | 개발 서버와 HMR 실행                 |
| `npm run typecheck`    | 앱·Vite 설정 TypeScript 타입 검사    |
| `npm run build`        | 타입 검사 후 `dist/`에 프로덕션 빌드 |
| `npm run preview`      | 빌드 결과 로컬 미리보기              |
| `npm run lint`         | ESLint 검사, 경고도 실패로 처리      |
| `npm run lint:fix`     | 자동 수정 가능한 린트 항목 수정      |
| `npm run format`       | Prettier 포맷 적용                   |
| `npm run format:check` | 파일을 수정하지 않고 포맷 검사       |
| `npm run check`        | 린트·포맷·타입 검사·빌드 전체 실행   |

빌드 결과를 확인하려면 `npm run build` 이후 `npm run preview`를 실행합니다.
preview 서버는 로컬 확인용이며 운영 서버가 아닙니다.

## API 요청 및 환경 변수

`frontend/.env.example`을 `frontend/.env.local`로 복사해 필요에 맞게 수정합니다.
기본값으로 실행한다면 복사하지 않아도 됩니다. 환경 변수 변경 후 개발 서버를 재시작하세요.
루트 `.env.local`은 백엔드·Compose용이며, 프런트엔드에서는 읽지 않습니다.

| 변수                | 기본값                  | 용도                             |
| ------------------- | ----------------------- | -------------------------------- |
| `VITE_API_BASE_URL` | `/api`                  | 브라우저가 요청할 API 기본 주소  |
| `BACKEND_URL`       | `http://localhost:8080` | Vite 프록시가 연결할 백엔드 주소 |

개발 시 `apiRequest('/v1/models')`는 `/api/v1/models`로 요청하고,
프록시가 `/api`를 제거해 `http://localhost:8080/v1/models`로 전달합니다.
`VITE_API_BASE_URL`을 별도 서버의 절대 URL로 지정하면 개발 프록시를 거치지 않으며,
해당 서버에 별도의 CORS 설정이 필요합니다.

`VITE_*` 값은 빌드 시 브라우저 코드에 포함됩니다. API 키·DB 비밀번호를 넣지 마세요.
`BACKEND_URL`은 Vite 설정에서만 사용합니다. 환경 예시에는 비밀 값을 넣지 않고,
실제 `.env.local` 파일은 Git에서 제외합니다.

공통 요청은 `src/api/client.ts`를 사용합니다.

```ts
import { apiRequest, ApiError } from '@/api/client'

const controller = new AbortController()

try {
  const result = await apiRequest<{ object: string; data: unknown[] }>(
    '/v1/models',
    { signal: controller.signal }, // 호출 측에서 만든 AbortController
  )
  // result를 화면에서 사용
} catch (error) {
  if (error instanceof ApiError) {
    // kind: http | network | invalid_response
    // status: HTTP 응답 코드, code: 백엔드 오류 코드(있는 경우)
  }
  // 사용자 취소는 AbortError 또는 signal.reason으로 구분
}
```

- 경로는 `/`로 시작하며, `/api` 접두사는 호출 시 다시 붙이지 않습니다.
- JSON 전송은 `{ method: 'POST', json: payload }`처럼 지정합니다.
- `headers`로 인증 헤더 등을 전달할 수 있습니다. 공통 모듈은 API 키를 저장하거나 자동 삽입하지 않습니다.
- 기존 `/v1/*`는 `Authorization: Bearer ...`가 필요합니다. 키 없이 호출하면 401이 반환됩니다.
- 기존 Router 키 인증과 향후 콘솔 로그인은 별개이며, 키 입력 UI·로그인 연동은 아직 구현하지 않았습니다.
- 204·205 응답은 `undefined`를 반환하므로 해당 호출에서는 `<void>` 타입을 사용합니다.
- JSON이 아닌 성공 응답은 `invalid_response`, HTML 등 비JSON 오류 응답은 상태 코드 기반 `http` 오류로 처리합니다.
- 제네릭 타입은 TypeScript 타입 지정이며, 응답 필드의 런타임 검증은 각 API 모듈에서 담당합니다.
- 자동 재시도·로그인 화면 이동·알림 표시는 수행하지 않습니다. 화면에서 오류 유형에 맞게 처리합니다.
- 기본 타임아웃은 없으며 `signal`로 취소 또는 타임아웃을 전달할 수 있습니다.
- 현재 모듈은 JSON API용입니다. 파일 업로드·다운로드·SSE는 별도 구현 대상입니다.

운영 빌드에는 Vite 프록시가 포함되지 않습니다. `/api`를 사용하려면 운영 웹 서버에
백엔드 전달 규칙을 설정해야 합니다. SPA fallback보다 API 규칙을 먼저 적용하세요.
환경 변수는 빌드 시 반영되므로 운영 API 주소를 변경하면 다시 빌드해야 합니다.

검증은 요청 모듈 단위 테스트와 로컬 HTTP 서버를 대상으로 한 Vite 프록시 통합 테스트로 수행합니다.
실제 Spring Boot 서버 인증 성공과 화면 데이터 표시는 다음 백엔드 연결 작업에서 확인합니다.

## 페이지 경로

| 경로         | 화면                        |
| ------------ | --------------------------- |
| `/`          | 홈                          |
| `/projects`  | 프로젝트                    |
| `/keys`      | API 키                      |
| `/usage`     | 사용량                      |
| `/logs`      | 요청 기록                   |
| `/providers` | 제공업체 연결               |
| `/settings`  | 설정                        |
| 그 외 경로   | 공통 레이아웃 안의 404 화면 |

홈 외 관리 페이지는 준비 중 화면입니다. 존재하지 않는 하위 경로도 404로 처리합니다.
`src/App.tsx`에서 경로를 구성하고, `src/layouts/ConsoleLayout.tsx`의 `Outlet`에 페이지를 표시합니다.
메뉴와 페이지 제목은 `src/navigation.ts`에서 함께 관리합니다.

개발 서버와 `npm run preview`는 SPA fallback을 제공합니다.
운영 정적 서버에서도 페이지 URL 요청에 `index.html`을 반환하는 SPA fallback이 필요합니다.
API 요청과 정적 파일 요청은 이 fallback에서 제외해야 합니다.
화면의 404는 클라이언트 라우팅 결과이며 HTTP 응답 상태 404를 의미하지 않습니다.
운영 서버 설정은 배포 작업에서 적용합니다.

`npm test`는 실제 Router와 DOM 환경에서 메뉴 이동, URL 직접 접근, 뒤로/앞으로 이동,
같은 URL로 재진입, 404 복귀를 검증합니다. `npm run check` 및 CI에도 포함됩니다.

## 경로 별칭과 협업 규칙

`@/`는 `frontend/src/`를 가리키는 경로 별칭입니다.
예를 들어 `import App from '@/App'`처럼 사용합니다.
TypeScript의 `paths`와 Vite의 `resolve.alias`에 동일하게 설정되어 있습니다.
같은 폴더 내에서는 상대 경로도 사용할 수 있습니다.
`src/` 내부 폴더는 레이아웃·라우팅 등 기능을 구현하면서 추가합니다.

- 의존성 추가·변경 시 `package.json`과 `package-lock.json`을 함께 반영합니다.
- 다른 개발 환경과 CI에서는 `npm ci`로 lockfile 기준 설치를 수행합니다.
- 편집기의 ESLint·Prettier·EditorConfig 지원을 활성화하면 저장 시 규칙을 적용할 수 있습니다.
- PR 생성 전 `npm run check`를 실행합니다. CI의 `Build and Test`에서도 동일 검사를 실행합니다.
- `node_modules/`와 `dist/`는 커밋하지 않습니다.

전체 프로젝트 구성과 백엔드 실행 방법은 [루트 README](../README.md)를 참고하세요.
