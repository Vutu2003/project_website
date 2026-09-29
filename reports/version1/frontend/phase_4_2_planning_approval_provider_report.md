# Phase 4.2 — Planning, Approval & Provider Workflow UI

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-27  
**Status:** **PASS**

## 1. Objective

Make UC01–UC07 usable in the local React application against the real frozen backend, with role-aware pages, versioned commands and visible results.

## 2. Starting Point

Phase 4.1 supplied Vite/React/TypeScript, login, sessionStorage JWT, central fetch client, role guards and the shell. Phase 3 supplied frozen UC01–UC12 business commands. Phase 1.3 supplied 603 canonical synthetic rows across 14 tables.

## 3. Scope

Implemented plan creation/edit/submit, director plan decisions, coverage routing, external provider proposal and director vendor decisions. No UC08–UC12 UI, backend workflow change, schema or migration was added.

## 4. Route / Page Architecture

`/plans` lists plans; `/plans/new` and `/plans/:id/edit` are VTYT command forms; `/plans/:id` shows plan/items and contextual route/proposal panels. `/approvals` and `/approvals/:id` are director queue/review pages. Existing dashboard, login, placeholders, unauthorized and 404 routes remain. Command buttons depend on role and current state; the backend still checks authorization.

## 5. API Modules

`plansApi`, `approvalsApi`, `providersApi`, `equipmentApi` and `departmentsApi` reuse the Phase 4.1 client. Pages do not call `fetch` directly. Methods, paths and JSON bodies mirror actual backend controllers/DTOs; no actor ID or client-assigned state is sent.

## 6. Shared Types / Enum Labels

`types/workflow.ts` types page and command DTOs. `utils/workflowLabels.ts` maps exact backend plan/item/approval/coverage/route enums to Vietnamese presentation labels. Status badges also contain readable text.

## 7. Plan List

The real `/api/plans` page supplies ten sorted rows with title, period, status, creator, date and version. Exact status filtering, loading, empty, retry and pagination are present. PHONG_VTYT sees the create entry point.

## 8. UC01 Create Plan

The form uses paged active equipment from `/api/equipment`, checks required title/dates/item count, date order and duplicate selection, and sends the selected IDs/dates to `POST /api/plans`. The returned ID opens the server-created plan. Browser smoke created an isolated four-item plan.

## 9. UC02 Edit Plan

The edit form loads the stored plan/items and sends `PATCH /api/plans/{id}` with the stored version. Existing items are retained under the V1 audit rule; users may edit dates or add equipment. Browser smoke edited a DRAFT and later a REVISION_REQUIRED plan.

## 10. UC03 Submit Plan

DRAFT plan detail confirms before versioned `POST /api/plans/{id}/submit`; busy state prevents double-click. Browser smoke observed SUBMITTED after reload.

## 11. UC04 Plan Approval

A director reviewed plan metadata and item context, requested revision with a reason, then approved the resubmitted round using `POST /api/approvals/{id}/decision`. The pending queue refreshed after each decision.

## 12. Approval Queue

The real PENDING queue comes from `/api/approvals/pending`, with request-type filter and pagination. Rows show request type, subject, requester and submission time. The detail page loads the separate review projection and subject context before a decision.

## 13. UC05 Coverage Routing

An APPROVED plan's PLANNED item presents coverage evidence from the new read-only endpoint. VTYT explicitly chooses `coverageId`; the route command includes current item version. Backend validation remains authoritative.

## 14. FREE Contract Route

Browser smoke selected verified FREE coverage and observed UNDER_CONTRACT plus the provider chosen by backend. The UI offered no arbitrary provider picker for this route.

## 15. NOT_FREE External Route

Browser smoke selected verified NOT_FREE coverage and observed PENDING_PROPOSAL. UNKNOWN evidence was visible but disabled; missing evidence gave an empty state and no route selection.

## 16. UC06 Vendor Proposal

The panel loads active providers, saves a DRAFT proposal, reads it back after refresh, and submits the selected provider/rationale/warranty note with the current item version. Browser smoke observed WAITING_VENDOR_APPROVAL.

## 17. UC07 Vendor Decision

The director review shows item/equipment, provider, coverage, rationale and warranty impact. Browser smoke first requested revision, then approved a new round; the item became ASSIGNED_EXTERNAL with backend-stored provider.

## 18. Revision Loops

The plan path completed SUBMITTED → REVISION_REQUIRED → DRAFT → SUBMITTED → APPROVED. The vendor path completed WAITING_VENDOR_APPROVAL → PENDING_PROPOSAL → WAITING_VENDOR_APPROVAL → ASSIGNED_EXTERNAL. Backend retained distinct approval rounds; UI did not overwrite history.

## 19. Optimistic Locking UX

All versioned commands carry the loaded plan/item version. A deliberate stale edit returned 409 `OPTIMISTIC_LOCK_CONFLICT`; the form showed a specific concurrency message and reload action. After reload, a fresh edit succeeded. No automatic retry or overwrite occurs.

## 20. Error Handling

The shared API client normalizes backend errors. The workflow feedback component distinguishes stale 409, other business 409, 403, 404 and network failures, and exposes reload where useful. Simple form validation gives readable inline alerts; final business rules stay on backend.

## 21. Role-Aware UI

PHONG_VTYT has plan and routing/proposal commands. BAN_GIAM_DOC has approval review/decisions and read-only plan context. KHOA_PHONG and ADMIN have no UC01–UC07 command controls. Direct wrong-role browser routes were checked; backend security tests cover direct command calls.

## 22. Responsive / Accessibility Basics

A 760px Chrome check initially found page-level overflow from wide table minimum width. Constraining grid children fixed it; plan list, create form and approval queue then passed with tables scrolling only inside their panels. Cards and tables remain readable. Forms use labels, status badges use words as well as color, buttons show busy text, and confirmations can be cancelled. This is a basic interaction review, not a formal WCAG audit.

## 23. Browser Validation

Chrome 154 used the real Vite frontend and packaged Spring backend at `localhost:5173`/`8080`. It exercised all seven UCs, four coverage situations, both revision loops, refresh during draft and wrong-role navigation. Representative list, create, detail, queue, routing, proposal and conflict screenshots were visually inspected. Screenshots remain temporary under `/tmp`, outside deliverables.

## 24. Browser Conflict Test

A second authenticated client updated the same plan after the browser loaded an earlier version. The browser's stale edit received 409 and offered reload; a new edit after reload succeeded. The injected 409 was intentional and did not corrupt data.

## 25. Network / Console Audit

Sixteen browser business commands carried a Bearer header from the central client. Request bodies contained backend-defined fields/version and no actor/status manipulation. Chrome recorded zero uncaught JavaScript errors, React warnings or CORS failures. Expected HTTP error entries came from deliberate negative/conflict checks.

## 26. Frontend Tests

`npm run build`, `npm run lint` and `npm run test` passed; Vitest **8/8** covered foundation and focused workflow behavior. Real-browser validation, rather than mocks alone, establishes the UI result.

## 27. Backend Regression

Focused and affected backend suites passed **44/44**: API 9, new workflow reads 3, provider routing 14, security 8, CORS 2 and planning/approval 8. The new endpoints are read-only DTO projections; Phase 3 command behavior and role checks remain unchanged.

## 28. Problems Found

The frozen write contract lacked a selectable coverage read, complete director proposal review and a way to reopen a saved DRAFT proposal after refresh. The app shell still had Phase 4.1 copy, and the first 760px check exposed page-level table overflow. Two early smoke script attempts made transient timing/redirect assumptions; the final browser run completed all flows.

## 29. Fixes Applied

Documented the read gaps first, then added three minimal GET endpoints with explicit DTOs and focused tests. Added typed frontend modules/pages, updated shell copy and constrained grid-child minimum widths. Adjusted the smoke harness to wait for actual page transitions. Guarded SQL cleanup removed only isolated `SMOKE` plan records created by the browser run.

## 30. What Is Not Implemented Yet

UC08 execution, progress and technical acceptance; UC09/10 handover; UC11 reporting; UC12 equipment-history UI; V2 repair, attachment upload, notifications and finance/contract screens. These remain future placeholders where applicable.

## 31. Phase 4.3 Handoff

Build UC08–UC10 UI on the existing auth/API/error patterns. Use backend execution/acceptance DTOs and version rules; preserve the Phase 4.2 role matrix and server-authoritative transitions. The local web remains runnable for human review.

## 32. Final Status

**PASS.** UC01–UC07 are visibly usable through the real browser/backend; conflict and role behavior were checked; frontend and affected backend tests pass. The canonical seed was restored: 14 tables, 117 columns, 31 FKs, six successful migrations, 603 business rows and zero remaining SMOKE plans. No schema/migration/business-state change.

## 33. Slide-ready Summary

- Phase 4.2 PASS: real React pages for UC01–UC07.
- VTYT creates/edits/submits plans; BGĐ reviews and decides.
- Coverage routing supports FREE and NOT_FREE; UNKNOWN/absent stays blocked.
- Vendor draft survives refresh; director revision and second-round approval work.
- Stale version returns visible 409 with reload, without silent overwrite.
- Four-role browser checks, clean console and Bearer command audit pass.
- Frontend build/lint/8 tests and affected backend 44 tests pass.
- Three minimal GET projections added; 14-table schema and 603-row seed unchanged.
