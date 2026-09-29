# Phase 4.1 — Frontend Audit

**Date:** 2026-09-27  
**Status:** **PASS**  
**Scope:** local frontend foundation and minimal backend CORS integration only.

## Environment

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| Node/npm | Project toolchain, Node 24 | 24.21.0 / 11.19.0 | PASS |
| Frontend URL | localhost:5173 | Browser opened `http://localhost:5173` (Vite binds loopback) | PASS |
| API URL | localhost:8080 | Packaged Spring Boot JAR, health UP | PASS |
| Database | Existing frozen dev database | Flyway validated V001–V006; Hibernate validation; 40 equipment in protected GET | PASS |
| Browser | Actual local browser | Google Chrome 154.0.8037.57, headless Chrome DevTools | PASS |

## Build

| Check | Result |
| --- | --- |
| `npm install --prefix frontend` | PASS, lockfile created |
| `npm run build --prefix frontend` | PASS, strict `tsc -b` and Vite 7.3.6 |
| `npm run lint --prefix frontend` | PASS, 0 errors/warnings |
| `npm run test --prefix frontend` | PASS, 4/4 Vitest |
| Focused backend security/CORS tests | PASS, 10/10, 0 failures |

The resolved runtime versions are React 19.3.0, React Router 7.18.4 and Vite 7.3.6. `node_modules/` and `dist/` are ignored.

## Routing

| Browser case | Observed | Result |
| --- | --- | --- |
| `/login` anonymous | Login page visible | PASS |
| `/` anonymous | Redirect to `/login`; no protected content | PASS |
| `/` authenticated | App shell and dashboard visible | PASS |
| VTYT opening BGĐ placeholder | `/unauthorized` with readable 403 UI | PASS |
| VTYT opening own placeholder | Honest “later frontend phase” page | PASS |
| Unknown URL | 404 page with home link | PASS |

## Authentication

| Case | Observed | Result |
| --- | --- | --- |
| Empty-field form | Required-fields message | PASS (Chrome) |
| Wrong password | Backend 401 mapped to readable message | PASS (Chrome) |
| Valid login | Real `POST /api/auth/login`, then `/api/auth/me` | PASS |
| Browser refresh | `/me` revalidates stored token; dashboard remains | PASS |
| Invalid stored token | `/me` 401, storage cleared, login shown | PASS |
| Logout | Token/user cleared; protected `/` redirects | PASS |
| Simulated backend outage | Readable connection error, no crash | PASS |

The automated browser entered credentials but never printed them. Session storage is the only persistence point; a stored user object is never trusted for restore.

## Role Mapping

| Demo account | Backend role | UI label | Expected menu | Result |
| --- | --- | --- | --- | --- |
| `demo_vtyt` | `PHONG_VTYT` | Phòng Vật tư Y tế | Plans, execution, reports | PASS |
| `demo_bgd` | `BAN_GIAM_DOC` | Ban Giám đốc | Approval | PASS |
| `demo_khoa_noi` | `KHOA_PHONG` | Khoa/Phòng | Equipment/history, handover | PASS |
| `demo_admin` | `ADMIN` | Quản trị hệ thống | Dashboard only | PASS |

Menu filtering is visual guidance; backend authorization remains authoritative.

## API Integration

The browser observed seven login POST requests (including negative cases) and ten `/me` GET requests (including restores/invalid token). Chrome DevTools extra-info confirmed every `/me` request carried a Bearer header. No token value was printed. The packaged backend independently returned health UP, login 200, `/me` 200, protected equipment 200 with 40 rows, and anonymous equipment 401.

## CORS

| Origin/request | Expected | Actual | Result |
| --- | --- | --- | --- |
| `http://localhost:5173` login POST preflight | Allow | HTTP 200 with exact allow-origin | PASS |
| `http://localhost:5173` Bearer GET preflight | Allow | HTTP 200; Authorization allowed | PASS |
| `http://unrelated.local:5173` Bearer GET preflight | Reject | HTTP 403; no allow-origin | PASS |
| Anonymous `/api/auth/me` with approved origin | 401, CORS header | 401 with exact allow-origin | PASS |

Only `/api/**` uses this CORS policy; the origin is configured by `FRONTEND_ORIGIN`. No wildcard or credentialed CORS is enabled. The two focused Java CORS regression tests passed.

## Browser Smoke

Chrome exercised login screen, invalid and valid login, four role labels/menus, dashboard, wrong-role route, allowed placeholder, refresh, logout, invalid-token restore, protected route, 404 and simulated offline behavior. Screenshots of desktop login/dashboard and narrow 560px login/dashboard were inspected. Neither narrow view had horizontal overflow. All functional assertions passed.

## Console Audit

There were **0 JavaScript console errors and 0 uncaught exceptions**. Five Chrome network log entries corresponded to deliberate 401/offline negative cases; none was a CORS failure. No React key warning or repeated request loop was observed in the instrumented run.

## Network Audit

`POST /api/auth/login` and `GET /api/auth/me` were observed from the browser. The client sent Authorization centrally on all `/me` calls. Responses/errors were rendered through DTO and normalized error logic. No raw JSON, JWT or password was placed in the UI. The dev build uses the externalized API base URL.

## Secret Audit

`rg` over `frontend/src` found no `console.log`, `console.debug`, `console.info`, `console.warn` or `console.error` calls. The only password references are the form field/request type; token references are the auth/API modules and tests. `.env.example` contains only the non-secret URL; `.env`, `.env.local`, `node_modules/`, `dist/` and `.local-postgres/` are ignored by `.gitignore`. Actual generated credentials were not written to reports or source.

## Git Hygiene

`git status --short` reports the entire project root as untracked (`?? backend/`, `?? frontend/`, etc.), a pre-existing repository baseline. A normal tracked diff cannot isolate this phase. Source files, lockfile and reports were inspected directly; no commit/push was made. No schema, migration, seed or business service was edited. Backend edits were limited to `SecurityConfig`, `application.yml`, `.env.example`, focused CORS tests and accurate CORS documentation.

## Known Limits

No business workflow UI or fake data was added. `sessionStorage` remains vulnerable to page-script compromise if XSS exists; no refresh token exists. The Chrome smoke was a local functional check, not a full accessibility, load or production-browser matrix.
