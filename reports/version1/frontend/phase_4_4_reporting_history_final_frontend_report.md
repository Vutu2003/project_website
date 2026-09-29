# Phase 4.4 — Reporting, Equipment History & Final Frontend Audit

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-28  
**Status:** **PASS — Frontend V1 frozen for human review**

## 1. Objective

Complete UC11–UC12 and audit the V1 frontend against the frozen backend through a real browser, regression tests and database cleanup.

## 2. Starting Point

Phase 4.1 supplied auth and shell; Phase 4.2 supplied UC01–UC07; Phase 4.3 supplied UC08–UC10 and a partial equipment-history read. Backend Phase 3.4 already had report commands and the hierarchical UC12 endpoint.

## 3. Scope

This phase changed frontend source/docs only. No backend service, schema, migration or seed file was modified. No V2 repair, attachment, notification, inventory, finance or deployment feature was started.

## 4. Routes / Page Architecture

Added `/reports`, `/plans/:planId/report`, `/equipment` and `/equipment/:equipmentId/history`. Removed completed `/workspace/:section` placeholder route and menu entries. RoleGuard and backend authorization both apply. Plan detail links reportable/reported plans to the report page.

## 5. API Modules / Shared Types

`reportsApi.ts` uses exact GET/POST/PUT/finalize paths. `historyApi.ts` owns one UC12 GET. `execution.ts` now models campaign state histories and report reference as returned. The Phase 4.3 execution page delegates history reads to this module and shares `AttemptHistory` with UC12. Pages use no raw fetch.

## 6. UC11 Reporting

The reporting list queries existing plans using backend status filters. Detail displays plan metadata, report status, narrative fields and distinct outcomes. It creates one DRAFT, edits it and finalizes it with explicit user confirmation.

## 7. Report Eligibility

The VTYT form appears only when the loaded plan is `AWAITING_REPORT` and the report is not FINAL. Backend rules decide actual eligibility. Backend 409/validation responses are displayed as returned safe messages.

## 8. Draft Report

The command sends the loaded plan `version` plus `reportNumber`, `workDone`, `achieved`, `notAchieved`, `causes`, `nextWork`, `resolutions` and `recommendations`. It sends no actor, count or status. After create/edit the page refetches plan and report. Browser refresh preserved the edited DRAFT.

## 9. Finalization

Finalization sends `{version}` to the exact endpoint. Browser verified report `DRAFT → FINAL` and plan `AWAITING_REPORT → REPORTED`, then refreshed and found a read-only report. Unsaved narrative edits require save before finalization. No `CLOSED` command exists.

## 10. Outcome Counts

Before report creation, item counts are labelled provisional. Once a report exists, the two numbers come from `ReportResponse.completedCount` and `repairRequiredCount`. A mixed smoke plan showed **1 completed + 1 repair-required** in both DRAFT and FINAL.

## 11. Report Conflict UX

A second client changed the report while the browser held an old plan version; browser edit received 409 `OPTIMISTIC_LOCK_CONFLICT`, offered reload and did not overwrite. A direct second POST received 409 with one report retained. FINAL hid edit/finalize controls. Deliberate 409s were negative tests, not application failures.

## 12. UC12 Equipment History

Equipment list reuses the scoped existing equipment endpoint, including inactive devices. Full history renders the single backend UC12 response, with no per-attempt/per-log fetches or mutation controls.

## 13. Multi-campaign History

All returned campaigns appear in backend newest-first order. Seed device `DEMO-EQ-004` showed its newer REPAIR_REQUIRED and older COMPLETED campaigns separately; the isolated full-flow device acquired another campaign, which also appeared first.

## 14. Multi-attempt History

Each attempt card shows server-assigned number, actual provider, start/end, progress, technical assessment and handover assessment. The mixed smoke plan retained three attempts after two technical failures and eventual PASS. Native expandable campaign sections keep the long view readable.

## 15. Failed Evidence

Seeded `DEMO-EQ-006` showed handover FAIL in an older campaign after expansion. Seeded `DEMO-EQ-004` showed technical FAIL. The mixed smoke plan showed earlier failed technical records beside the successful later attempt. No evidence was rewritten.

## 16. REPAIR_REQUIRED

The history card uses a distinct “Chuyển sửa chữa” label and explains that this is the Maintenance V1 hand-off boundary. The mixed smoke item stayed REPAIR_REQUIRED and was never counted as COMPLETED.

## 17. Department Scope

KHOA_PHONG opened an allowed device history and received 403 on a foreign device without identity leakage. The UI renders only campaigns returned by the backend, preserving historical-department filtering and masked current custody for former departments. No frontend inference grants access.

## 18. Report Reference in History

Campaigns with a report show status and dates. VTYT/BGD receive a link to their readable report. KHOA does not receive the link, and backend redaction of unfinished report references is preserved.

## 19. Shared History Components

`AttemptHistory` and its acceptance display are shared by execution and UC12 history. One type/read model prevents a different interpretation of attempts, providers or failures between screens.

## 20. Role-Aware UI

VTYT has plan/execution/report/history actions; BGD has approval and report/history reads; KHOA has scoped equipment/history and handover; ADMIN has no invented UC11/UC12 privilege. Menus contain no completed-feature placeholder.

## 21. Responsive / Accessibility

Chrome checked report list/detail and equipment list/history at 1366px and 760px without page-level horizontal overflow. Internal tables scroll. Form controls have labels; statuses use text and color; native `<details>` supports keyboard expansion and focus styling. No formal WCAG certification is claimed.

## 22. UC01–UC12 Frontend Coverage

UC01–03 plan form/detail; UC04 approval review; UC05–06 coverage/vendor proposal; UC07 vendor decision; UC08–09 execution/technical; UC10 handover with co-signer; UC11 reporting; UC12 history. All have working routes and backend-bound actions/reads.

## 23. Final Route Audit

`/login`, `/`, `/plans`, `/plans/new`, `/plans/:planId`, `/plans/:planId/edit`, `/approvals`, `/approvals/:requestId`, `/execution`, `/execution/plans/:planId`, `/plans/:planId/items/:itemId/execution`, `/reports`, `/plans/:planId/report`, `/equipment`, `/equipment/:equipmentId/history`, `/unauthorized`, 404 fallback. Report/execution detail sidebar highlighting was corrected.

## 24. Final Role Matrix

| UC | PHONG_VTYT | BAN_GIAM_DOC | KHOA_PHONG | ADMIN |
| --- | --- | --- | --- | --- |
| 01–03 plan | Command | — | Scoped read | — |
| 04 approval | — | Command | — | — |
| 05–06 routing/proposal | Command | — | — | — |
| 07 vendor decision | — | Command | — | — |
| 08–09 execution/technical | Command | — | — | — |
| 10 handover | Second signer | — | Command | — |
| 11 report | Command/read | Read | — | — |
| 12 history | Broad read | Broad read | Scoped read | — |

## 25. API Contract Audit

The typed report module sends only narrative and version on create/edit, and only version on finalize. History uses one GET per page load and the same DTO as Phase 4.3. Central client owns base URL, Authorization and JSON error normalization. Tests asserted request paths/methods and absence of actor/status/count fields.

## 26. Error UX Audit

400 validation, 401 session expiry, 403 role/scope, 404 missing resource, business 409, optimistic 409 and network unavailable all follow the shared safe error path. Browser checked missing history 404, wrong-department 403, stale report 409 and duplicate report 409. No Java stack or credential was displayed.

## 27. Optimistic Lock Audit

Phase 4.2 plan edit/submit/routing and Phase 4.3 execution/acceptance retain versioned commands and reload actions. UC11 sends current loaded plan version and never silently retries stale writes. Browser reproduced a concurrent report edit conflict.

## 28. Two-signer Regression

The full V1 browser flow completed KHOA handover PASS with temporary VTYT co-signer credentials. Primary KHOA session token remained unchanged; the secondary token was not put in sessionStorage. The mixed plan repeated the two-signer completion after rework.

## 29. Browser V1 End-to-End Flow

Real Chrome created isolated plan #161, submitted, obtained BGD approval, selected FREE coverage, started work, appended progress, finished, recorded technical PASS, completed KHOA handover with VTYT co-signer, created/edited/refreshed/finalized BM03 and opened equipment history. Every state was reloaded from backend. This is the complete representative UC01→UC12 path.

## 30. Browser Failure/Rework Flow

Isolated plan #162 had FREE and NOT_FREE items. Browser routed both, saved/reopened/submitted an external proposal and obtained BGD approval. The FREE item failed technical acceptance twice, then passed on its third attempt and completed two-signer handover. The external item reached REPAIR_REQUIRED. Browser finalized a mixed 1+1 report, then verified all attempts and repair reason in UC12.

## 31. Console / Network Audit

No uncaught browser JavaScript exception, React warning, CORS failure or infinite request loop was observed. Deliberate 403/404/409 requests produced expected HTTP failures; Chrome logged the intentional foreign-history 403 as a network error line. Source inspection found no `console.log`/`dangerouslySetInnerHTML` or hardcoded credential. Report/history calls use the typed central client.

## 32. Frontend Tests

`npm run build --prefix frontend`, `npm run lint --prefix frontend` and `npm run test --prefix frontend` all passed. Vitest: **20/20** across eight files, including request wiring, report counts/FINAL controls, multi-campaign rendering and failed attempt preservation.

## 33. Backend Regression

After smoke cleanup, `BackendBusinessFinalIntegrationTest` 11, `ExecutionAcceptanceIntegrationTest` 13, `WorkflowReadIntegrationTest` 3 and `SecurityIntegrationTest` 8 passed: **35/35**. An earlier run before cleanup failed only baseline-count assertions because the two intentionally live smoke plans raised 8 plans to 10; cleanup and rerun resolved it. Backend source was untouched.

## 34. Database Cleanup

A guarded transaction deleted only smoke plans #161/#162 and their report, acceptance, progress, execution, approval, history and item rows. Final checks: zero `SMOKE-%` plans, **603 business rows**, **14 tables / 117 columns / 31 FKs**, six successful V001–V006 migrations.

## 35. Problems Found

The old navigation pointed UC11/UC12 at placeholders. Phase 4.3 duplicated attempt markup. A browser rework run exposed a local FAIL radio selection that persisted into a new attempt. A first backend regression run occurred before isolated smoke cleanup and tripped fixed-count assertions. Report detail initially highlighted the Plans menu.

## 36. Fixes Applied

Added real UC11/UC12 routes and role menu entries; extracted shared `AttemptHistory`; reset technical/handover selection on each new attempt; corrected report/execution sidebar highlighting; reran backend regression on the restored canonical fixture. Report finalization now requires saving any unsaved edits first.

## 37. Known Limitations

No refresh token, V2 repair, attachments, notifications, production deployment/load proof, formal WCAG or cross-browser certification. Session storage is a local V1 choice needing production browser-security review. No full external-provider completed end-to-end command path was rerun in this phase; Phase 4.3 covered external completion and Phase 4.4 covered external proposal/approval through repair hand-off and report.

## 38. Frontend V1 Freeze

The routes, role-aware navigation, typed API modules, DTO/error handling, version/reload behavior, shared attempt evidence, report lifecycle and scoped UC12 presentation form the V1 frontend baseline. See [frontend-v1-freeze.md](../../frontend/docs/frontend-v1-freeze.md).

## 39. Phase 5 Handoff

Human review can start at `http://localhost:5173/login`. Phase 5 should integrate and measure the frozen V1 journeys and review production concerns without inventing CLOSED or repair V2 behavior. The local frontend/backend were left runnable.

## 40. Final Status

**PASS.** UC11/UC12 are usable in the browser; full V1 and failure/rework flows passed; frontend 20/20 and affected backend 35/35 passed; canonical schema and 603-row seed were restored.

## 41. Slide-ready Summary

- Frontend V1 covers UC01–UC12 with role-aware routes and backend-enforced access.
- UC11 creates/edits DRAFT, finalizes to FINAL/REPORTED and keeps completed vs repair counts distinct.
- UC12 shows every scoped campaign and attempt, including failed technical/handover evidence and report references.
- A full contract V1 browser path and a mixed FREE/NOT_FREE failure/rework path passed.
- A rework radio-state defect was fixed; old evidence stayed immutable.
- Frontend build/lint/20 tests and backend 35 tests passed.
- Guarded cleanup restored 603 rows and the frozen 14/117/31 schema with V001–V006.
