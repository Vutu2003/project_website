# Frontend V1 Freeze

## 1. Stack

React 19, TypeScript 5, Vite 7, React Router 7, native fetch and CSS. The build is a client-side SPA served locally at `http://localhost:5173`.

## 2. Authentication

The auth provider checks `/api/auth/me` on refresh; the central client attaches the Bearer token. The token lives in tab-scoped `sessionStorage`; an expired token clears the session. No refresh token exists.

## 3. Routing

The active routes are `/login`, `/`, `/plans`, `/plans/new`, `/plans/:planId`, `/plans/:planId/edit`, `/approvals`, `/approvals/:requestId`, `/execution`, `/execution/plans/:planId`, `/plans/:planId/items/:itemId/execution`, `/reports`, `/plans/:planId/report`, `/equipment`, `/equipment/:equipmentId/history`, `/unauthorized` and the 404 fallback. Completed feature placeholders were removed.

## 4. Role Navigation

| Use case | PHONG_VTYT | BAN_GIAM_DOC | KHOA_PHONG | ADMIN |
| --- | --- | --- | --- | --- |
| UC01–03 plan create/edit/submit | Command | — | — | — |
| UC04 plan approval | — | Command | — | — |
| UC05–06 coverage route/vendor proposal | Command | — | — | — |
| UC07 vendor decision | — | Command | — | — |
| UC08–09 execution/technical | Command | — | — | — |
| UC10 handover | Second signer for PASS | — | Command | — |
| UC11 report | Command/read | Read | — | — |
| UC12 history | Broad read | Broad read | Scoped read | — |

Menus reflect these working areas. Backend policies enforce access independently of route guards. Authenticated plan reads are additionally scoped for KHOA_PHONG by the backend.

## 5. UC01–UC12 Screens

Plan list/form/detail, director queue/review, coverage/provider panel, execution queue/item, report list/detail and equipment list/history cover all V1 use cases. UC02 retains created items for audit. Repair ends at the V1 hand-off boundary.

## 6. API Modules

`client.ts` owns the API base URL, JSON, Bearer and safe errors. `plansApi`, `approvalsApi`, `providersApi`, `executionsApi`, `acceptancesApi`, `reportsApi`, `equipmentApi` and `historyApi` own typed endpoint calls. Pages make no raw fetch call. Phase 4.3 execution and Phase 4.4 history use the same `historyApi` DTO and `AttemptHistory` display.

## 7. State / Version Handling

Commands send loaded plan/item versions. On success the UI reloads authoritative rows. A 409 optimistic conflict offers reload and never silently retries. Report DRAFT/FINAL and plan AWAITING_REPORT/REPORTED are read directly; no UI-created state transition exists.

## 8. Error Handling

400 field validation, 401 session expiry, 403 role/scope, 404 missing resource, 409 business/optimistic conflict and connection errors use the shared safe presentation. Unexpected JavaScript details are not shown to users.

## 9. Multi-attempt Presentation

Every numbered attempt keeps its provider, progress and technical/handover evidence. Failed results remain on screen after rework. Starting a new attempt resets the local technical/handover radio selection to PASS while retaining the old stored evidence.

## 10. Department Scope

KHOA_PHONG plan, equipment, handover and UC12 reads use backend historical-department scope. The history UI renders only returned campaigns; current custody is masked when the backend omits it. Wrong-department access shows 403 without foreign details.

## 11. Two-signer Handover

A KHOA_PHONG PASS uses a temporary VTYT login token only in `X-VTYT-Authorization`; the primary token stays in `Authorization`. The secondary token is not persisted. The browser verified the primary session survives handover.

## 12. Reporting

VTYT may create/edit one DRAFT and finalize it. BGD may read. FINAL is immutable in the UI. Backend-derived COMPLETED and REPAIR_REQUIRED counts remain separate. There is no CLOSED command.

## 13. Equipment History

One scoped backend history response renders all campaigns in returned order, all attempts, progress, assessments, status chronology and report references. No history mutation controls exist.

## 14. Responsive / Accessibility Baseline

Report and history pages passed desktop and 760px page-overflow checks. Tables scroll internally. Fields have labels; statuses include text; native details and buttons are keyboard operable with visible focus. There is no formal WCAG certification.

## 15. Test / Browser Validation

Frontend build and lint pass; Vitest passes 20/20. Chrome exercised a full contract route from plan creation through report and history, a mixed FREE/NOT_FREE plan, vendor approval, technical failure/rework, two-signer handover and repair hand-off. Finalized report counts were 1 COMPLETED + 1 REPAIR_REQUIRED. Seeded technical/handover failures remained visible. Deliberate 403/404/409 responses were handled; no uncaught JS exception was observed. Backend report/history/security/execution regressions passed 35/35. Guarded smoke cleanup restored 14 tables, 117 columns, 31 FKs, six migrations and 603 rows.

## 16. Known V1 Limitations

There is no refresh token, V2 repair workflow, attachments, notifications, production deployment or load proof. `sessionStorage` remains readable by same-origin JavaScript and requires production security review. No formal WCAG or cross-browser certification was performed. The local fixture is synthetic.

## 17. Phase 5 Handoff

Human review can begin with `/reports`, `/equipment` and the V1 route matrix. Phase 5 may test integrated journeys and nonfunctional behavior using the frozen backend/frontend contracts; it should not assume a repair V2 or CLOSED command exists.
