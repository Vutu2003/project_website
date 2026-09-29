# Phase 4.1 — Frontend Bootstrap, Authentication & App Shell

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-27  
**Status:** **PASS**

## 1. Objective

Make the frozen backend visibly usable in a browser: real login, verified JWT session, protected shell, role-aware navigation and logout. No business workflow screen was built.

## 2. Starting Point

The Phase 3.4 backend had UC01–UC12 business paths and an unchanged 14-table schema. `frontend/` contained only a placeholder README. The backend used exact four-role JWT identity, but intentionally had no browser CORS origin. The [business freeze](../../backend/docs/backend-business-freeze.md), [security guide](../../backend/docs/security-guide.md), [API guide](../../backend/docs/api-foundation-guide.md), [reporting/history guide](../../backend/docs/reporting-history-workflow.md), [Phase 3.4 audit](../backend/phase_3_4_reporting_history_final_audit_report.md), controllers/DTOs/configuration and env examples were inspected before implementation.

## 3. Frontend Stack

React 19.3.0, React Router 7.18.4, TypeScript 5.9.3, Vite 7.3.6, native `fetch` and plain CSS. Node 24.21.0/npm 11.19.0 came from `scripts/use-toolchain.sh`. Vitest 3.2.7 and ESLint 9 provide small validation gates. No state manager or UI framework was needed.

## 4. What Are React / Vite / TypeScript?

**React** renders the browser interface from reusable components and state. **Vite** starts the fast local development server and builds static assets. **TypeScript** checks API shapes and component code before build, catching missing/wrong fields.

## 5. Frontend Architecture

```text
Browser → React + React Router → central fetch client → Bearer JWT → Spring Boot → PostgreSQL
             ↑                         ↑
         AuthContext              sessionStorage
```

A **SPA** (single-page application) keeps one HTML page and changes views in JavaScript. **Client-side routing** maps URL paths to React components. The browser still calls the real Spring API for identity; no mock login exists.

## 6. Project Structure

`frontend/src/api` handles HTTP and errors; `auth` owns token/session and guards; `layouts` owns the shell; `routes` owns menu configuration; `pages` owns screens; `components` contains loading/error fallbacks; `types` and `utils` hold the exact identity contract and role labels. `package-lock.json` records resolved packages.

## 7. Routing

`/login` is public; `/` is the protected dashboard; `/unauthorized` is the protected role-denied page; unknown paths show 404. A few `/workspace/:section` routes show honest placeholders for future work. React Router declarative mode is used.

## 8. Login Screen

The page has labeled required username/password inputs, password masking, Enter-capable form submission, loading button and safe inline errors. It contains no password defaults or business mock data. A wrong password displays a readable Vietnamese message.

## 9. Authentication Flow

`POST /api/auth/login` returns `{accessToken, tokenType, expiresIn, user}`. The frontend checks Bearer type, verifies the new token with `GET /api/auth/me`, then stores the token and current backend identity (`id`, `username`, `role`, `departmentId`). Navigation continues to the requested protected route or `/`. **JWT Bearer authentication** means the browser sends a signed access token in the Authorization header on protected requests.

## 10. Token Storage Decision

`sessionStorage` was chosen over `localStorage`: it survives refresh in the same tab but is removed when the tab closes. It is still accessible to page JavaScript, so XSS remains a token-theft risk; production security review is required before handling real hospital data. No refresh token or cookie was invented. Passwords are never stored.

## 11. Session Restore

At startup AuthProvider reads the token through one storage module and calls `/api/auth/me`. Protected content waits during verification, so it does not flash before authorization. A 401 clears token/user and returns to login with an expiry message. Network unavailability keeps the stored token but shows a retry view instead of trusting stale identity.

## 12. API Client

One typed `fetch` wrapper reads `VITE_API_BASE_URL` (default `http://localhost:8080`), adds Bearer centrally, parses JSON and emits one session-expired event on authenticated 401. Future Phase 4.2 modules can add plan/approval/provider API calls alongside `authApi`.

## 13. Error Handling

Backend `ErrorResponse(status, code, message, fieldErrors, ...)` becomes `ApiError`; failed connection becomes `NetworkError`. The UI shows a short message rather than raw JSON, exception class or stack trace. A small React error boundary prevents an unexplained blank page after a render failure.

## 14. CORS Integration

`SecurityConfig` now enables Spring Security CORS for `/api/**` with one configurable `FRONTEND_ORIGIN` (default `http://localhost:5173`), methods GET/POST/PUT/PATCH/OPTIONS, and explicit Authorization, Content-Type and handover co-signer headers. Credentials are not enabled; wildcard origin is not used. Actual Chrome calls and OPTIONS tests succeeded; an unrelated origin received 403 without allow-origin. No business API, DTO, schema or migration changed.

## 15. App Shell

The authenticated layout has a navy sidebar, system title, user name, role badge, logout and main content area. The home page shows current username, role and department ID from `/me`, plus a note that business screens follow later. It does not query or invent business metrics.

## 16. Role-Aware Navigation

Menu entries come from one configuration. VTYT sees plan/execution/report placeholders; BGĐ sees approval; Khoa/Phòng sees equipment/history and handover; ADMIN sees dashboard. Friendly Vietnamese labels map the exact backend role strings. Hiding a menu is UX, **not backend authorization**.

## 17. Protected Routes

`ProtectedRoute` redirects anonymous visitors to `/login` only after initialization. `RoleGuard` redirects a signed-in user to `/unauthorized` for another role's placeholder. The backend continues to enforce actual route/data permissions.

## 18. Logout

Logout removes the session token and user locally, then navigates to `/login`. A refresh stays logged out; revisiting `/` redirects to login. The stateless backend has no logout endpoint.

## 19. UI / Styling Foundation

A light academic/hospital palette uses navy, teal and gray, plain CSS, thin borders, simple cards and clear spacing. Login, dashboard and narrow-login Chrome screenshots were visually reviewed. No complex animation or large component framework was added.

## 20. Responsive / Accessibility Basics

At a narrow 560px viewport, both login and authenticated dashboard remained readable without horizontal overflow; the app shell changes to compact horizontal navigation below 680px. Labels, button text, semantic header/nav/main, alert/status regions and visible focus states are present. This is not a full WCAG audit.

## 21. Local Browser Validation

Real Chrome 154 (headless) opened `http://localhost:5173/login` against the running Vite server and real packaged backend. It verified visible login, wrong-password feedback, valid VTYT dashboard, `/me` identity, refresh restore, logout, anonymous redirect, wrong-role unauthorized page, allowed placeholder, 404, invalid token cleanup and a simulated network outage. Browser screenshots were inspected.

## 22. Four-role Login Validation

All four synthetic accounts logged in successfully via the real backend: `demo_vtyt` → PHONG_VTYT / Phòng Vật tư Y tế; `demo_bgd` → BAN_GIAM_DOC / Ban Giám đốc; `demo_khoa_noi` → KHOA_PHONG / Khoa/Phòng; `demo_admin` → ADMIN / Quản trị hệ thống. Each showed its role-specific navigation and `/me` succeeded. No password is in the UI or report.

## 23. Build / Lint / Tests

`npm run build` passed strict TypeScript and Vite output; `npm run lint` passed with no warnings; `npm run test` passed **4/4** focused Vitest tests. The focused backend SecurityIntegrationTest and CorsIntegrationTest passed **10/10**. Packaged-JAR smoke confirmed health UP, login 200, `/me` 200, equipment GET 200/40 and anonymous equipment 401.

## 24. Browser Console / Network Audit

Chrome reported **0 JavaScript console errors and 0 uncaught exceptions**. Seven login POSTs and ten `/me` GETs were observed during negative, restore and four-role scenarios. Chrome network extra-info confirmed Bearer headers on all `/me` GETs without printing values. Five network log entries came from deliberately rejected credentials/invalid tokens and the simulated offline case; none was a CORS error. No infinite retry loop occurred.

## 25. Security Notes

The backend validates JWT, active account, current role and department scope. Frontend menus/guards cannot grant access. `sessionStorage` is a development trade-off and no real patient/hospital data is included. Token/password values were not printed in the app source, browser audit output or report. Both `.env` and local generated security files are ignored.

## 26. Problems Found

The backend initially lacked an allowed development browser origin. The first TypeScript build needed Vite environment types; the first lint run found an unused error-boundary method and a mixed context/component export. Browser automation initially checked refresh before Chrome finished navigation. These were integration/setup issues, not business-contract defects.

## 27. Fixes Applied

Added exact-origin backend CORS and two regression tests; added `vite-env.d.ts`; separated AuthContext and removed the unused method; synchronized browser smoke on completed reload. Final build, lint, tests, CORS, visual and browser flow checks passed.

## 28. What Is Not Implemented Yet

No plan creation/edit, approval queue, provider routing, execution, acceptance/handover, report or equipment-history UI. No fake equipment/plan data, refresh token, notification, deployment or production security/load claim was added.

## 29. Phase 4.2 Handoff

Add `plansApi`, `approvalsApi`, `providersApi` and pages using the existing client, guards, role labels and ErrorResponse model. Keep backend version fields and 409 conflicts visible in writes; refresh after mutations. Start with UC01–UC07 as planned. Business state decisions remain on the backend.

## 30. Final Status

**PASS.** The real backend and local Vite app remain runnable at `http://localhost:8080` and `http://localhost:5173`. Phase 4.1 acceptance checks passed. Human review can use the four demo accounts from the ignored local security env file.

## 31. Slide-ready Summary

- Phase 4.1 PASS: React/TypeScript/Vite app connects to frozen Spring backend.
- Real four-role JWT login and `/me` verification; session restores after refresh.
- Protected shell, role-aware navigation, logout, unauthorized and 404 views.
- Exact-origin CORS allows localhost:5173; unrelated origin is rejected.
- Frontend build/lint and 4 tests pass; backend focused security/CORS 10 tests pass.
- Real Chrome browser smoke and console/network audit pass; no business screen started.
