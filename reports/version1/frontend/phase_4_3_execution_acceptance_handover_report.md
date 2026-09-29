# Phase 4.3 — Execution, Technical Acceptance & Handover UI

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-28  
**Status:** **PASS**

## 1. Objective

Make UC08–UC10 usable in the real local React application while preserving every execution attempt and the frozen backend's role, version, department and two-signer rules.

## 2. Starting Point

Phases 4.1/4.2 supplied JWT login, central fetch/error handling, role guards, paged plan pages and provider routing. Phase 3.3 supplied named execution and acceptance commands. The canonical PostgreSQL fixture had 14 tables and 603 synthetic rows.

## 3. Scope

Implemented start, progress, finish, repair hand-off, technical PASS/FAIL, rework, department handover PASS/FAIL and second VTYT authentication. UC11 reports, UC12 full equipment history and Repair V2 remain outside this phase.

## 4. Route/Page Architecture

`/execution` is a paged visible-plan entry; `/execution/plans/:id` lists scoped items; `/plans/:planId/items/:itemId/execution` holds summary, current commands and all attempts. Plan detail links relevant items. VTYT and KHOA_PHONG have the Phase 4.3 routes; director/admin have no command page.

## 5. API Modules

`executionsApi.ts` and `acceptancesApi.ts` use the central client. Requests target exact item/execution IDs and frozen payload fields. Handover PASS uses a temporary VTYT token in the custom header while the client keeps the KHOA Bearer token in `Authorization`.

## 6. Execution Read Model

The existing department-scoped `GET /api/equipment/{id}/maintenance-history` already includes every attempt, progress log, technical and handover record, actual provider and signer IDs/times. After loading a scoped plan item, the page selects its matching campaign. No backend read endpoint or DTO change was necessary. It does not expose a full UC12 equipment-history screen.

## 7. UC08 Start Execution

VTYT sends current item `version` and plan `planVersion` to `POST /api/plan-items/{id}/executions`. A routed or REWORK_REQUIRED item shows the action. The backend assigns attempt number and may move APPROVED plan to IN_PROGRESS. Browser tests observed attempt 1 and a new attempt 2 after rework.

## 8. Actual Provider Display

Each attempt card shows `actualProviderName` read from backend history. Contract smoke showed the coverage provider; external smoke showed the director-approved outside provider. No provider or attempt number is entered on the start form.

## 9. Progress Logging

The current active attempt appends a nonblank work note and optional damage note. Server timestamp and recorder ID display in chronological evidence. There are no edit/delete controls. Browser smoke retained separate notes on attempts 1 and 2.

## 10. Complete Work

VTYT confirms `POST /api/executions/{id}/complete-work` with item version and optional result note. The page reloads AWAITING_TECHNICAL_ACCEPTANCE; its text distinguishes finished work from accepted work.

## 11. Repair Hand-off

An active attempt accepts a required reason through `POST /api/executions/{id}/repair-required`. Smoke reached REPAIR_REQUIRED, kept damage/reason evidence and removed all further execution/acceptance actions. The plan became AWAITING_REPORT because its only item was terminal.

## 12. UC09 Technical Acceptance

VTYT assesses the latest ended attempt by exact execution ID, current item version, PASS/FAIL and nonblank conclusion. A repair checkbox is available only for FAIL. The page refreshes stored assessment and item state.

## 13. Technical PASS

PASS moved the latest attempt to AWAITING_HANDOVER in both contract and external browser flows. Handover controls were absent until this current-attempt record existed.

## 14. Technical FAIL / Rework

FAIL with repair flag false moved the item to REWORK_REQUIRED. The browser then started a new attempt, observed server-assigned number 2 and confirmed the first failed assessment remained on attempt 1. A separate FAIL/repair choice is available from the actual backend DTO.

## 15. Multi-attempt Preservation

Attempt cards are ordered by server-assigned `attemptNo`; the latest is labelled current. Cards show actual provider, times, result note, progress, technical result and handover result. Browser refresh after rework displayed both cards. Old cards expose no command controls.

## 16. UC10 Handover

A KHOA_PHONG user can submit only while the item is AWAITING_HANDOVER and the latest attempt has technical PASS. The command uses the latest execution ID, item version, result, conclusion and optional FAIL repair flag. Backend checks eligibility again.

## 17. Department Scope

Plan/item reads and equipment history already filter KHOA_PHONG to historical `department_id_at_plan`. The browser's Khoa Nội account could not open an intensive-care handover target and saw no equipment detail. The backend service remains the final 403 guard on commands.

## 18. Two-signer Authentication

For PASS, the page requests a second VTYT username/password, calls `/api/auth/login` without replacing AuthContext, checks the returned role, and passes the temporary token only in `X-VTYT-Authorization`. Credentials clear after success/failure; the token is never stored, rendered or logged. Browser comparison confirmed the primary KHOA session token stayed unchanged.

## 19. Handover PASS

Contract and external flows recorded PASS and became COMPLETED. Attempt evidence showed stored KHOA/VTYT signer IDs and server times. The single-item smoke plans moved to AWAITING_REPORT.

## 20. Handover FAIL / Rework

The browser submitted FAIL without a second signer, observed REWORK_REQUIRED, started attempt 2, checked that the earlier technical PASS did not enable handover during active work, then completed technical PASS and signed handover PASS on attempt 2. Four assessments stayed on their respective attempts.

## 21. REPAIR_REQUIRED

REPAIR_REQUIRED is displayed separately from COMPLETED. The terminal note identifies V1's repair hand-off boundary; history remains visible and no restart, acceptance or handover button appears.

## 22. COMPLETED

COMPLETED displays a final badge and read-only attempt history. No execution command appears. When all items are terminal, a neutral AWAITING_REPORT note points to later UC11 work.

## 23. Optimistic Conflict UX

A second authenticated client started the same item after Chrome loaded its old versions. Chrome's stale start returned 409 `OPTIMISTIC_LOCK_CONFLICT`, showed reload, and after reload there was exactly one attempt. No silent retry or duplicate attempt occurred.

## 24. Role-Aware UI

VTYT sees start/progress/finish/repair/technical controls. KHOA_PHONG sees scoped handover and read context. BGĐ and ADMIN receive no Phase 4.3 command route. Backend URL and business-service checks remain authoritative.

## 25. Responsive / Accessibility

Desktop and 760px Chrome checks found no page-level overflow on execution queue or two-attempt page. Attempt cards collapse to one column at 760px. Forms have visible labels, explicit PASS/FAIL controls, text badges, busy states and native confirmation cancellation. No formal WCAG audit was performed.

## 26. Browser Happy Path

Contract and external plans each completed start → progress → finish → technical PASS → KHOA handover PASS with VTYT co-signer → COMPLETED. External attempt displayed the approved outside provider. Final database evidence: one attempt, one progress log and two assessments per happy path.

## 27. Browser Rework Path

Technical FAIL produced a retained attempt 1 and fresh active attempt 2. Handover FAIL produced two attempts, two progress logs, four assessments and final COMPLETED. Refresh retained both attempts. A rapid double click produced only one technical acceptance request.

## 28. Browser Repair Path

An active attempt logged work/damage, then a required repair reason. The item became REPAIR_REQUIRED; its only plan became AWAITING_REPORT. Progress and reason remained visible; no V1 continuation control appeared.

## 29. Wrong Department / Signer Tests

The Khoa Nội account's direct URL to another department's handover showed authorization feedback without equipment details. Missing VTYT credentials gave visible local validation. Admin as second signer was rejected; the KHOA session stayed intact. Correct VTYT credentials completed PASS. Existing backend tests verify missing/non-VTYT header rejection directly.

## 30. Network / Console Audit

Final Chrome smoke observed 28 business commands, each with primary Bearer authorization and without client actor/status fields. Unit tests verified exact primary and secondary handover headers. Chrome recorded zero uncaught JavaScript errors and zero React console errors/warnings; expected negative 403/409 responses were part of tests. No CORS error appeared.

## 31. Frontend Tests

Focused tests cover command paths/bodies, attempt ordering, exact two-token handover headers, primary-session retention and safe local error presentation. Final `npm run build`, `npm run lint` and Vitest **14/14** passed (five test files).

## 32. Backend Regression

No backend code changed. Representative Phase 3.3 execution (13), security (8) and CORS (2) suites passed **23/23**, zero failures/errors/skips. Flyway validated unchanged V001–V006; a test-only injected history failure logged an expected rollback error during the suite.

## 33. Problems Found

First Chrome run exposed that local validation errors were hidden by the generic unexpected-error formatter. Two subsequent smoke assertions assumed the first attempt card held new attempt progress; the database showed progress correctly on attempt 2. The existing history endpoint already supplied all needed evidence, so no backend read gap remained.

## 34. Fixes Applied

Introduced `UserInputError` so only intentional local messages are displayed, while unexpected JavaScript details stay hidden. Added a regression test. Corrected the browser harness to inspect all attempt cards. Re-ran full Chrome flow on fresh isolated fixtures; it passed.

## 35. What Is Not Implemented Yet

UC11 reporting UI, UC12 full equipment-history UI, V2 repair, attachment upload, inventory, notification, finance, analytics, production load and full accessibility certification.

## 36. Phase 4.4 Handoff

Build reporting and full equipment history on existing auth/API patterns. Reuse the final AWAITING_REPORT plan and retained attempt evidence; preserve distinct COMPLETED and REPAIR_REQUIRED outcomes. The local web remains runnable for human review.

## 37. Final Status

**PASS.** UC08–UC10 are usable through the real browser/backend, including multi-attempt, two-signer and negative authorization paths. Five isolated smoke plans were removed; canonical seed returned to 603 rows. Final metadata: 14 business tables, 117 columns, 31 FKs and six successful migrations. No backend/schema/migration/seed source was changed.

## 38. Slide-ready Summary

- Phase 4.3 PASS: real UI for UC08–UC10.
- VTYT starts attempts, logs progress, finishes work and records technical results.
- Khoa/Phòng handover PASS uses a second authenticated VTYT signer.
- Contract and external happy paths reached COMPLETED with actual providers shown.
- Technical and handover rework retained failed attempt evidence and created attempt 2.
- Repair hand-off ended at REPAIR_REQUIRED; stale start returned visible 409 without duplicate.
- Real Chrome: 28 commands, clean console; desktop/760px layout checks passed.
- Frontend build/lint/14 tests and backend 23/23 regression passed; frozen schema and 603-row seed remain intact.
