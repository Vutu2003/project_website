# Phase 4.4 — Full Frontend V1 Audit

**Date:** 2026-09-28  
**Result:** **PASS** after smoke cleanup and repeat regression.

## Environment

Local Vite `http://localhost:5173`, Spring Boot `http://localhost:8080` health `UP`, project PostgreSQL 16 on loopback port 55432 and headless Chrome CDP. Four generated demo credentials came from ignored `.local-postgres/backend-security.env`; values were never recorded in reports. Browser tests used isolated `SMOKE-P44-%` plans #161/#162, later deleted.

## UC01–UC12 Matrix

| UC | V1 screen | Browser evidence |
| --- | --- | --- |
| UC01 | `/plans/new` | Created isolated plan #161 |
| UC02 | `/plans/:id/edit` | Prior Phase 4.2 revision regression retained; route audited |
| UC03 | Plan detail submit | #161 sent for approval |
| UC04 | Director queue/review | BGD approved #161 and #162 |
| UC05 | Plan detail coverage panel | FREE and NOT_FREE selected |
| UC06 | Vendor draft/proposal | #162 saved, reopened and submitted |
| UC07 | Director vendor decision | #162 external route approved |
| UC08 | Execution item | Start, progress, finish, repair hand-off |
| UC09 | Execution item | PASS and FAIL/rework/third-attempt PASS |
| UC10 | Execution item | KHOA PASS with VTYT co-signer twice |
| UC11 | Report list/detail | Draft, edit, refresh, stale 409, FINAL/REPORTED, 1+1 |
| UC12 | Equipment list/history | Multiple campaigns/attempts, repair, failure, scope |

## Route Matrix

| Route | Access / outcome |
| --- | --- |
| `/login` | Public login |
| `/` | Authenticated dashboard |
| `/plans`, `/plans/:planId` | Authenticated read, backend KHOA scope |
| `/plans/new`, `/plans/:planId/edit` | VTYT action UI |
| `/approvals`, `/approvals/:requestId` | BGD review UI |
| `/execution`, `/execution/plans/:planId` | VTYT/KHOA queues |
| `/plans/:planId/items/:itemId/execution` | VTYT/KHOA scoped workflow |
| `/reports`, `/plans/:planId/report` | VTYT write/read, BGD read |
| `/equipment`, `/equipment/:equipmentId/history` | VTYT/BGD broad, KHOA scoped |
| `/unauthorized`, wildcard | Forbidden UX, 404 page |

The old `/workspace/:section` placeholder route and completed-feature menu items were removed. Report/execution detail sidebar selection was corrected.

## Role Matrix

| UC action | VTYT | BGD | KHOA | ADMIN |
| --- | --- | --- | --- | --- |
| 01–03 plan commands | Yes | No | No | No |
| 04 plan decision | No | Yes | No | No |
| 05–06 route/propose | Yes | No | No | No |
| 07 vendor decision | No | Yes | No | No |
| 08–09 work/technical | Yes | No | No | No |
| 10 handover | Co-sign PASS | No | Primary | No |
| 11 report | Write/read | Read | No | No |
| 12 history | Broad | Broad | Historical scope | No |

RoleGuard hides forbidden screens, while backend enforces the real policy. Direct ADMIN report/history routes redirected to `/unauthorized`; KHOA report route did likewise. Wrong-department history GET returned 403.

## API Module Audit

`client.ts` owns base URL, fetch, JSON, Bearer and normalized errors. Auth, plans, approvals, providers, executions, acceptances, reports, equipment and history each have typed modules. No page calls raw fetch. Report POST/PUT uses narrative plus `version`, finalize uses only `version`; no actor/status/count is submitted. History loads one hierarchical endpoint per page load, with no row-by-row HTTP fanout.

## Auth / Session Audit

Refresh restores identity through `/api/auth/me`; 401 clears the tab session. Logout clears the primary token. Handover's temporary co-signer token is not persisted. No frontend source logs passwords or JWTs. Route guards are UX controls, not substitutes for backend authorization.

## Error UX Audit

400 validation and local blank-field feedback are readable. 401 follows auth restore/expiry. 403 role/scope hides foreign identity. 404 missing equipment shows safe missing-data feedback. Business 409 preserves the backend message; optimistic 409 displays a reload action. Network failure uses the central `NetworkError`. No stack traces are exposed.

## Optimistic Lock Audit

Plan edit/submit, routing, execution and acceptance retain loaded-version commands and reload feedback from earlier phases. UC11 browser held v7 while a second client updated the report; stale PUT returned 409, then reload fetched the latest narrative/version. No automatic retry or duplicate write occurred.

## Department Scope Audit

KHOA saw allowed equipment history; a foreign device returned 403 without a device name. Backend current/historical custody rules supply list entries and filter campaigns. UC12 displays only returned campaigns and respects masked current-department, coverage/provider, plan-wide history and unfinished-report fields. Former-department behavior is preserved by backend contract; no moved-department smoke fixture was created in this phase.

## Multi-attempt Audit

The execution page and UC12 page use the same `AttemptHistory`. Seeded failed technical and handover records were visible after expanding older campaigns. Isolated #162 showed attempts 1, 2 and 3 after FAIL, FAIL, then PASS. Starting a fresh attempt now resets local radio selection; persisted prior evidence remains unchanged.

## Two-signer Audit

Chrome completed two KHOA handovers with VTYT secondary login. The primary session token remained unchanged; the temporary token was sent only in `X-VTYT-Authorization`. Earlier negative credential and role tests remain in the Phase 4.3 suite. Backend execution/security regression passed after cleanup.

## Reporting Audit

The list queries `AWAITING_REPORT` or `REPORTED` through the plan API. DRAFT created, edited and survived refresh; stale update and duplicate create returned safe 409. FINAL made the report read-only and changed plan to REPORTED. The mixed report showed 1 COMPLETED and 1 REPAIR_REQUIRED, from backend counts after creation. No CLOSED command exists.

## History Audit

`DEMO-EQ-004` had two separate campaigns, newer REPAIR_REQUIRED and older COMPLETED. `DEMO-EQ-006` retained handover FAIL. The isolated #162 campaign showed three attempts and report reference; its external item showed a repair reason. No history mutation control exists. Empty state, 403 and 404 routes were checked.

## Responsive Audit

Chrome at 1366px and 760px checked report list/detail and equipment list/history. Document width stayed within viewport; wide tables scroll inside their panel. Campaign, attempt and report grids collapse on narrower screens. Representative report screenshot was visually inspected.

## Accessibility Baseline

Inputs/textarea have visible labels, statuses have text as well as color, buttons and native details work by keyboard, focus is visible on expandable summaries, and errors use alerts. Formal WCAG and assistive-technology certification remain outside V1.

## Browser E2E Audit

Plan #161: VTYT create/submit → BGD approve → FREE route → VTYT execution/progress/finish/technical PASS → KHOA handover PASS with VTYT co-signer → VTYT DRAFT/edit/refresh/finalize → FINAL/REPORTED → UC12 history. Plan #162: FREE + NOT_FREE route, vendor draft/submit/BGD approval, technical FAIL/rework/FAIL/rework/PASS, KHOA handover, external repair hand-off, mixed 1+1 report and UC12 history. The extra technical FAIL was caused by a local radio selection persisting across attempts; the UI defect was fixed before final validation.

## Console Audit

No uncaught JavaScript exception, React warning or CORS failure during final checks. Chrome logged the deliberately requested foreign-history 403 as a network error, classified as expected. Browser development debug/info messages carried no credential value. No infinite request loop was observed.

## Network Audit

Report and history requests used exact frozen paths/methods. The central client attached primary Bearer centrally; report command bodies excluded actor/status/count. UC12 made one full-history GET per load, not one request per attempt or progress row. A repeated navigation legitimately made another GET. No JWT value was printed or saved in this audit.

## Secret Audit

Source search found no `console.log`, `dangerouslySetInnerHTML`, hardcoded demo password or token dump. Password handling remains in login and temporary co-signer forms; generated values stay in ignored env files. Test literals such as `test-token` are synthetic. No source PDF, backend configuration, migration or seed was edited.

## Frontend Test Regression

Build PASS, ESLint PASS, Vitest **20/20** in eight files. Focused additions cover report/history wire contract, separate outcome counts, FINAL control hiding, campaign order, read-only history and failed attempt preservation. The pre-existing two-signer and stale-conflict tests still pass.

## Backend Regression

After cleanup, `BackendBusinessFinalIntegrationTest` 11, `ExecutionAcceptanceIntegrationTest` 13, `WorkflowReadIntegrationTest` 3 and `SecurityIntegrationTest` 8 passed, **35/35**. A first run with two smoke plans still present produced only seed-count cleanup assertions; rerun on the canonical seed passed. No backend source change was made.

## Database Cleanup

Guarded deletion matched exactly two `SMOKE-P44-%` plans owned by demo_vtyt and exactly three items before deleting dependencies. Final: zero `SMOKE-%` plans, 603 business rows, 14 tables, 117 columns, 31 FKs, six successful V001–V006 migrations. Local backend/frontend remain running for human review.
