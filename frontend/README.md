# Frontend

React·TypeScript·Vite 기반 LLM Router 관리 콘솔입니다.
현재는 공통 레이아웃의 시각적 프로토타입과 개발 도구가 구성되어 있습니다.
사이드바 메뉴 전환·검색·접기, 모바일 메뉴, 계정 팝오버와 시스템·라이트·다크 테마를 확인할 수 있습니다.
테마 선택은 브라우저에 저장하며, 시스템 모드는 OS 테마 변경을 따릅니다.
메뉴는 React Router의 BrowserRouter로 이동하며 URL 직접 접근·새로고침·뒤로/앞으로 이동을 지원합니다.
인증·프로젝트 선택·백엔드 연결은 후속 작업입니다.

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
