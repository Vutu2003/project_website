# Phase 3.2 — Coverage, Provider Selection & Assignment

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-26  
**Status:** **PASS**

## 1. Objective

Complete UC05–UC07: determine verified maintenance coverage, route FREE work to its contracted provider, propose and obtain director approval for a NOT_FREE external provider, and leave items ready for Phase 3.3. Enforce the routing portion of BR02/BR03 without starting execution.

## 2. Starting Point

Phase 3.1 already provided plan creation, revision and director plan approval with transactions, role checks, audit history and versioned commands. Phase 2 provided PostgreSQL/Flyway/JPA, DTO/error conventions and JWT identity. The canonical fixture has 603 synthetic rows on 14 frozen business tables.

## 3. Business Rules BR02 / BR03

BR02's relevant gate is enforced: UC05–UC07 command mutations require a parent plan in `APPROVED`. A valid route alone does not start maintenance. BR03 requires verified FREE coverage to use its contracted provider; verified NOT_FREE coverage to use a submitted and approved vendor request; and UNKNOWN/missing coverage to block routing.

## 4. Coverage Model

`MaintenanceCoverage` holds equipment, classification, provider, effective dates, verifier/time and basis. The route command requires an explicit `coverageId` because no source-backed rule chooses between multiple applicable rows. Applicability uses the item's `plannedDate`, otherwise the plan's `periodStart`; non-null effective bounds must contain that date. Nullable bounds remain open under the frozen dictionary.

## 5. Why UNKNOWN Is Different From NOT_FREE

UNKNOWN means evidence is insufficient; NOT_FREE is a verified conclusion that the device is outside free contractual maintenance. Treating missing/UNKNOWN as NOT_FREE would allow an external proposal without evidence. The service returns safe 409 errors and leaves the item PLANNED, with no request, provider or transition history.

## 6. UC05 Route Determination

`POST /api/plan-items/{itemId}/route` checks VTYT role, expected item version, APPROVED plan, PLANNED item, same-equipment coverage, VTYT verifier/time/basis and date. It writes the selected coverage and exactly one status transition. The System Analysis permits preliminary assessment before plan approval; this Phase 3.2 command persists a route only after approval, as required by the scoped BR02 gate.

## 7. FREE Coverage Path

FREE changes `PLANNED → UNDER_CONTRACT`, sets `assignmentRoute=UNDER_CONTRACT` and derives `assignedProvider` from the coverage row. The contracted provider must exist and be active. The client supplies no provider ID for this route, so it cannot substitute an unrelated vendor. The transition and assignment are atomic; no ApprovalRequest is created.

## 8. NOT_FREE Coverage Path

NOT_FREE changes `PLANNED → PENDING_PROPOSAL` and links the verified coverage. Provider and assignment route remain empty until director approval. The service does not infer NOT_FREE from an absent row or UNKNOWN classification.

## 9. UC06 External Provider Proposal

`POST /api/plan-items/{itemId}/vendor-proposals` saves a DRAFT `VENDOR_SELECTION` ApprovalRequest while the item stays PENDING_PROPOSAL. It can hold provider, rationale and warranty-impact note, with incomplete draft fields allowed. `POST /api/vendor-proposals/{requestId}/submit` can complete those fields, requires an active provider and nonblank rationale, marks the request PENDING and changes the item to WAITING_VENDOR_APPROVAL with one history row. Each command is transactional. The System Analysis's save-draft alternative and frozen FD-02 are reconciled by storing a DRAFT request that is not yet pending BGĐ action.

## 10. ApprovalRequest Reuse

The frozen request table represents both PLAN_APPROVAL and VENDOR_SELECTION with exclusive typed targets. Phase 3.2 adds no proposal table. The service allows at most one active draft and one pending vendor request per item; the existing partial unique index protects the pending case. A later revision creates a new request, retaining old decisions.

## 11. UC07 Director Decision

The existing `/api/approvals/{requestId}/decision` dispatches by request type to separate transactional services, each of which reloads and validates its target. Only BAN_GIAM_DOC may decide a pending vendor request for a WAITING_VENDOR_APPROVAL item on an APPROVED plan. APPROVE creates an immutable action, resolves the request and sets `ASSIGNED_EXTERNAL`, `EXTERNAL_APPROVED` and the exact proposed provider. REVISION_REQUIRED requires a nonblank comment, resolves the request and returns the item to PENDING_PROPOSAL with no assignment. Both append one history row.

## 12. Vendor Revision Loop

A real-PostgreSQL integration test executes `PLANNED → PENDING_PROPOSAL → WAITING_VENDOR_APPROVAL → PENDING_PROPOSAL → WAITING_VENDOR_APPROVAL → ASSIGNED_EXTERNAL`. It verifies two distinct requests, a revision and an approval action, immutable first-round evidence, the director reason, and final provider equality with the approved second proposal.

## 13. Item State Machine

Phase 3.2 adds only five edges: PLANNED→UNDER_CONTRACT, PLANNED→PENDING_PROPOSAL, PENDING_PROPOSAL→WAITING_VENDOR_APPROVAL, WAITING_VENDOR_APPROVAL→ASSIGNED_EXTERNAL and WAITING_VENDOR_APPROVAL→PENDING_PROPOSAL. No IN_MAINTENANCE or plan IN_PROGRESS transition is implemented.

## 14. Provider Evidence Integrity

FREE provider comes directly from selected coverage. External provider comes directly from the director-approved VENDOR_SELECTION request. The item retains the NOT_FREE coverage row used for its route. A provider disabled before decision causes 409 and no decision. Wrong-equipment, non-VTYT verifier and expired coverage are rejected even when their FKs are individually valid.

## 15. Role Authorization

PHONG_VTYT alone routes and creates/submits vendor proposals. BAN_GIAM_DOC alone decides a pending vendor request. KHOA_PHONG and ADMIN have no UC05–UC07 command privilege. Spring Security restricts paths, and services repeat the checks using `CurrentUser`. Anonymous requests return 401; wrong roles return 403. Actor IDs/times are server derived.

## 16. Transactions

`MaintenanceAssignmentService` owns four `@Transactional` commands: route, create draft, submit draft and decide vendor. Route updates item/provider/coverage/history together; submission updates request/item/history together; decision updates action/request/item/history together. A test-only failure in history writing after action save produced a safe error and rolled the entire decision back, leaving request PENDING and item unassigned.

## 17. StatusHistory

Every real item transition has one append-only history event with exact old/new state and actor. The FREE and NOT_FREE actions use the Phase 1.3 seed's `VERIFY_FREE_COVERAGE` and `CLASSIFY_NOT_FREE`; vendor submit uses `SUBMIT_VENDOR`; approval uses `RECORD_VENDOR_APPROVAL`. Vendor revision uses `RECORD_VENDOR_REVISION` with the required reason. Draft creation writes no false transition.

## 18. Optimistic Locking

Each item-changing command requires expected `MaintenancePlanItem.version`. Successful transitions increment it and return the resulting DB version. A saved draft leaves the version unchanged because the item itself does not change. Stale route/proposal/decision inputs return safe 409 and leave request, assignment and history untouched. Hibernate `@Version` protects concurrent commits.

## 19. Command API

| Method | Path | Actor | Outcome |
| --- | --- | --- | --- |
| POST | `/api/plan-items/{itemId}/route` | PHONG_VTYT | FREE contracted route or NOT_FREE proposal-ready item |
| POST | `/api/plan-items/{itemId}/vendor-proposals` | PHONG_VTYT | Create DRAFT vendor request; 201 |
| POST | `/api/vendor-proposals/{requestId}/submit` | PHONG_VTYT | Submit DRAFT, make item WAITING |
| POST | `/api/approvals/{requestId}/decision` | BAN_GIAM_DOC | Typed plan or vendor decision |

The existing GET plan-item and pending-approval APIs reflect new state, provider, route and queue membership. The shared decision route returns one of two explicit flat DTO types through a sealed response interface, never a JPA entity.

## 20. Business Errors

The existing ErrorResponse contract reports 400 for malformed/missing proposal content, 401/403 for authentication/role, 404 for missing target, and 409 for state/version/evidence conflicts. Named conflicts include `PLAN_NOT_APPROVED`, `COVERAGE_REQUIRED`, `COVERAGE_UNKNOWN`, `COVERAGE_NOT_APPLICABLE`, `COVERAGE_EQUIPMENT_MISMATCH`, `PROVIDER_INACTIVE`, `PENDING_VENDOR_APPROVAL_EXISTS` and `OPTIMISTIC_LOCK_CONFLICT`. No SQL, JWT, hash or stack trace appears in client errors.

## 21. Integration Tests

Fourteen new `ProviderRoutingIntegrationTest` methods run through HTTP/JWT on the real isolated PostgreSQL 16 database. They cover FREE/NOT_FREE happy paths, draft completion, revision rounds, planned-date anchor, read queue/DTO compatibility, role matrix, stale versions and rollback. Result: **14/14 PASS**.

## 22. BR03 Negative Tests

UNKNOWN and absent coverage remain PLANNED; wrong-equipment, non-VTYT verifier and expired coverage fail; a planned date beyond coverage end overrides an earlier plan start; nonexistent/inactive provider and missing rationale are rejected; duplicate draft/pending attempts and wrong item state are rejected. Each test checks no unintended request, action, assignment or history evidence remains.

## 23. Authorization Tests

Anonymous route, draft, submit and decision return 401. BGD, KHOA_PHONG and ADMIN cannot issue VTYT commands; VTYT, KHOA_PHONG and ADMIN cannot decide vendor requests. A direct service call under a KHOA_PHONG principal is also rejected, proving authorization is not only in URL rules.

## 24. Approval Round Tests

Round 1 revision and round 2 approval each retain their own request/action; no DECIDED row is reopened. Duplicate decision returns 409. The shared decision endpoint still dispatches PLAN_APPROVAL to Phase 3.1 and VENDOR_SELECTION to Phase 3.2; both regression paths pass.

## 25. Phase 3.1 Regression

All eight Phase 3.1 tests pass. One old test assumed the shared HTTP decision path must reject a vendor request; that expectation became obsolete when UC07 was deliberately added. It now checks safe stale vendor HTTP behavior and directly verifies `PlanApprovalService` still rejects a vendor request with `APPROVAL_REQUEST_WRONG_TYPE`. The plan revision loop, BR01, item retention and stale-plan checks remain intact.

## 26. Phase 2 Regression

All 36 Phase 2 foundation/persistence/repository/API/security tests pass unchanged. Total final backend suite: **58 tests, 0 failures, 0 errors, 0 skips**. Both a clean-rebuild `mvn clean test` and an independent `mvn clean package` passed.

## 27. Database Regression

A guarded reset targeted only `medical_maintenance_backend_dev`; the six unchanged Flyway V001–V006 migrations ran on an empty DB, followed by original Phase 1.3 seeds and four demo login hashes. After tests and JAR smoke cleanup: **14 tables, 117 columns, 31 FKs, six migrations, 603 business rows, zero TEST/SMOKE plans**. No V007, schema, migration, seed or Phase 1 DB change.

## 28. Problems Found

The old Phase 3.1 wrong-type HTTP test conflicted with the new authorized typed dispatch. A generic `Object` return on the shared controller would obscure the DTO-only boundary. Coverage could have multiple rows without a source-backed automatic tie-breaker. Source UC06 calls save-draft an alternative while the frozen design represents it as a DRAFT request. The repository tree remains untracked in Git, limiting ordinary `git diff` review.

## 29. Fixes Applied

A small facade now delegates plan/vendor decisions to their separate services, with a sealed response DTO family. The Phase 3.1 regression verifies its service-specific wrong-type guard and the shared HTTP behavior. UC05 requires an explicit coverage ID and validates the planned-date anchor. UC06 stores an unsubmitted DRAFT request as specified by frozen FD-02. Source files, database metadata and SHA-256 digests were reviewed directly where Git could not show a tracked diff.

## 30. What Is Not Implemented Yet

UC08–UC12, maintenance execution, progress logs, technical acceptance, handover, reporting, repair V2 and frontend work remain outside this phase. No new plan transition, direct status endpoint, assignment-history table, vendor account or migration was introduced.

## 31. Phase 3.3 Handoff

UC08–UC10 must revalidate plan approval/in-progress state, item route/provider/coverage evidence and, for external work, the matching approved request/action before starting an execution attempt. `maintenance_execution.provider_id` must record the actual provider per attempt. Phase 3.2 stops at UNDER_CONTRACT or ASSIGNED_EXTERNAL; no work has started.

## 32. Final Status

**PASS.** UC05–UC07, the relevant BR02 gate and BR03 evidence paths are implemented with role/version checks, atomic history and preserved approval rounds. Clean rebuild, 58/58 tests, independent package and packaged-JAR workflow smoke passed; the 14/117/31 schema and 603-row canonical seed remain intact.

## 33. Slide-ready Summary

- Phase 3.2 routes verified FREE coverage to the contracted provider; NOT_FREE requires a director-approved external proposal.
- UNKNOWN and missing coverage block routing; expired and wrong-equipment evidence is rejected.
- Vendor drafts, submission and revision rounds reuse ApprovalRequest/ApprovalAction without new tables.
- Five item transitions are audited atomically with actor and optimistic version checks.
- PHONG_VTYT proposes; BAN_GIAM_DOC decides; other roles cannot issue these commands.
- 58/58 tests, clean rebuild, final JAR smoke and 14/117/31/603 database regression pass.
