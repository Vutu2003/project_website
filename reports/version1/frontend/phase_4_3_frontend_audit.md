# Phase 4.3 Frontend Audit — UC08–UC10

**Date:** 2026-09-28  
**Result:** **PASS**

## Environment

React 19/TypeScript 5/Vite 7 at `http://localhost:5173`; packaged Spring backend at `http://localhost:8080`; PostgreSQL 16 private cluster at loopback port 55432. Chrome 154 used the real Phase 1.3 synthetic fixture and five isolated browser plans. No mock server was used for browser checks.

## Routes

`/execution` → `/execution/plans/:planId` → `/plans/:planId/items/:itemId/execution`. Phase 4.2 plan detail links relevant items. PHONG_VTYT and KHOA_PHONG routes use RoleGuard; backend read/command checks are authoritative. UC11/UC12 pages remain future work.

## UC08 Audit

PASS — start sent exact item and plan versions. Backend assigned actual provider and attempt number. First start moved the plan to IN_PROGRESS; rework created a separate numbered attempt. Stale concurrent first start returned 409 and did not create a duplicate.

## Progress Audit

PASS — work and optional damage notes append to the active current execution. The UI shows server timestamp and recorder ID in historical order, with an empty state and no edit/delete control. Progress on attempt 2 remained separate from attempt 1.

## Complete Work Audit

PASS — current item version and optional result note target the exact execution ID. A confirmation precedes the command. Item reloads as AWAITING_TECHNICAL_ACCEPTANCE; the UI does not call it accepted yet.

## Repair Audit

PASS — nonblank reason on active execution produced REPAIR_REQUIRED and retained damage/progress/reason. No rework/technical/handover control remained. Its plan became AWAITING_REPORT, while the item remained clearly separate from COMPLETED.

## UC09 Audit

PASS — technical form targets only latest ended attempt in the required item state and sends exact PASS/FAIL, conclusion, version and repair flag. Backend continues to enforce all eligibility and uniqueness rules.

## Rework Audit

PASS — technical FAIL retained its assessment on attempt 1, moved to REWORK_REQUIRED and allowed a new attempt 2. Handover FAIL similarly retained failed evidence and led to a new attempt. A rapid double click triggered one technical request due to a synchronous in-flight guard and busy button.

## UC10 Audit

PASS — only current-attempt technical PASS with AWAITING_HANDOVER offered KHOA handover. PASS with second VTYT token became COMPLETED; FAIL without second token became REWORK_REQUIRED. Browser later completed the second handover attempt.

## Department Scope Audit

Khoa Nội direct navigation to canonical intensive-care plan 5/item 21 returned authorization feedback; equipment detail did not render. The page obtains item through department-scoped plan reads and selects its campaign from the backend's scoped history response. Backend service tests separately verify wrong-department command 403 using historical department.

## Two-signer Audit

For PASS, `/api/auth/login` obtains a temporary PHONG_VTYT token; the handover request keeps the primary KHOA `Authorization` and puts the temporary token only in `X-VTYT-Authorization`. Missing credentials show a safe visible message. ADMIN credentials are rejected before handover; the primary session remains unchanged. Credentials clear after submission/failure or switching to FAIL; secondary token is never stored/rendered/logged. Backend tests verify missing/wrong-role header rejection directly.

## Multi-attempt Audit

Each attempt card shows backend number, actual provider, start/end, result note, progress, technical/handover evidence and safe signer IDs/times. Latest attempt is labelled current; prior cards are read-only. Browser refresh after attempt 2 preserved both cards. An old technical PASS did not unlock handover during the new active attempt.

## Role Matrix

| Feature | PHONG_VTYT | BAN_GIAM_DOC | KHOA_PHONG | ADMIN |
| --- | --- | --- | --- | --- |
| View execution context in Phase 4.3 UI | Read | No page | Scoped read | No page |
| Start execution | Command | No | No | No |
| Append progress | Command | No | No | No |
| Complete work | Command | No | No | No |
| Repair hand-off | Command | No | No | No |
| Technical acceptance | Command | No | No | No |
| View handover context | Read | No page | Scoped read | No page |
| Handover decision | No | No | Scoped command | No |

BAN_GIAM_DOC can read the separate equipment-history backend API under UC12's existing policy; Phase 4.3 adds no director execution page.

## Version / Conflict Audit

An authenticated second client started an item after Chrome had loaded the previous plan/item versions. Browser start received `OPTIMISTIC_LOCK_CONFLICT` 409, offered reload and then displayed exactly one server-created attempt. No silent retry or local status override. Other business errors display backend messages; local validation uses `UserInputError`, unexpected JavaScript details remain generic.

## API Contract Audit

`executionsApi` uses named item/execution endpoints and exact DTO fields. `acceptancesApi` uses exact acceptance IDs/body fields and optional custom second-signer header. Browser command bodies contained no actor IDs or status assignment. Unit tests verified primary/secondary headers and no secondary storage. Full attempt read uses existing `GET /api/equipment/{id}/maintenance-history` and selects matching plan/item after scoped plan reads; no backend change was needed.

## Browser Smoke

Five isolated plans covered contract, external, technical rework, handover rework and repair. Final database evidence before cleanup: contract/external each had one attempt, one progress, two assessments and COMPLETED; technical rework had two attempts with first FAIL; handover rework had two attempts, two progress and four assessments with final COMPLETED; repair had one attempt, two progress rows and REPAIR_REQUIRED. Screenshots for routed, active, handover, completed, rework and repair states were inspected temporarily under `/tmp`. Desktop and 760px views had no page-level overflow.

## Console Audit

Final Chrome run: **0 uncaught JavaScript errors**, **0 React console errors/warnings**, no observed CORS error or repeated-fetch loop. Deliberate negative 403/409 responses appeared only as expected network outcomes.

## Network Audit

Final browser flow recorded **28 business commands** with primary Bearer token and no client `actorId`/`status` keys. Methods/paths target frozen commands. A focused API test checked the exact `X-VTYT-Authorization` handover header beside unchanged primary `Authorization`; successful browser PASS confirms backend accepted the pair.

## Secret Audit

Source scan found no hardcoded demo credential, token/password logging, `dangerouslySetInnerHTML` or storage write for the secondary token. `authApi.login` for the second signer uses `auth:false`, so its failed login cannot clear the primary KHOA session. Secondary password is a password input, cleared after request. Temporary screenshots contain no credentials or tokens.

## Frontend Tests

Final `npm run build`, `npm run lint` and Vitest **14/14** passed (five test files). Focused tests cover wire shapes, signer isolation, attempt ordering, 409 feedback and safe local error text.

## Backend Regression

No backend code changed. ExecutionAcceptanceIntegrationTest **13/13**, SecurityIntegrationTest **8/8**, CorsIntegrationTest **2/2**: total **23/23 PASS**. Flyway validated six unchanged migrations. The execution suite logged one intentionally injected test-only history failure to verify rollback; its assertion passed.

## Cleanup

A guarded transaction selected exactly five `SMOKE-P43-%` plans owned by `demo_vtyt` and their five items, checked for no report, and deleted only dependent acceptance, progress, execution, approval, status-history, item and plan rows. Database query after cleanup returned **0 SMOKE plans and 603 business rows**. Final metadata: 14 business tables, 117 columns, 31 FKs and six successful migrations. The source schema, migrations and seed files were not edited.
