# Phase 3.1 — Planning & Plan Approval

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-26  
**Status:** **PASS** for the agreed audit-preserving UC02 item policy

## 1. Objective

Implement UC01–UC04 and BR01 as transactional planning and director approval commands without changing the frozen database or Phase 2 API/security foundations.

## 2. Starting Point

Phase 2.6 supplied Java 17/Spring Boot, PostgreSQL 16, six Flyway migrations, 14 JPA entities/repositories, eight read-only business GET routes, DTO/error conventions, four-role JWT security, and 603 synthetic demo rows. The frozen schema is 14 tables, 117 columns and 31 FKs. No Phase 3 command existed before this work.

## 3. What Is a Business Service?

A business service checks who may act, whether the current state permits it, and which records must change together. `PlanningService` owns UC01–UC03; `PlanApprovalService` owns UC04. Controllers only accept HTTP input and return DTOs.

## 4. Why Transactions Matter

`@Transactional` makes a command all-or-nothing. A submission must commit the plan's `SUBMITTED` state, its pending request and history together. If any insert fails, none remain. The same rule covers create/items/history and director decision/action/request/state/history.

## 5. Implemented Use Cases

| UC | Result |
| --- | --- |
| UC01 | Create a draft plan with planned equipment items and creation history |
| UC02 | Edit draft/revision plan, add items or update planned dates; revision save returns to draft |
| UC03 | Submit a valid draft and create a new pending plan approval round |
| UC04 | Director approves or requests revision with preserved decision evidence |

## 6. Planning Workflow

VTYT selects active equipment for a period, saves a draft, edits it if needed and submits it. BGĐ decides the pending request. Revision requires VTYT to save the plan back to DRAFT before another submission; approval ends this Phase 3.1 path.

## 7. Plan State Machine

Implemented transitions: `DRAFT → SUBMITTED`, `SUBMITTED → APPROVED`, `SUBMITTED → REVISION_REQUIRED`, `REVISION_REQUIRED → DRAFT`. Existing later statuses are still readable but no Phase 3.1 command moves into them. A state transition changes a stored status under a named, validated command.

## 8. UC01 Create Plan

`POST /api/plans` accepts title, period and at least one item. The service validates dates, active/existing equipment and no duplicate equipment in the same plan. It derives creator and department snapshot from trusted server data. Plan begins `DRAFT`, items `PLANNED`; plan/item creation history is inserted in the same transaction. A duplicate-equipment request fails after the first item insert and rolls back the entire new plan.

## 9. UC02 Edit Plan

`PATCH /api/plans/{id}` accepts expected version and replacement plan metadata plus optional additive item changes. Existing planned dates may change, new equipment may be added, and omitted items remain. DRAFT edits record an `EDIT_PLAN` same-state audit event; a revised plan records `REVISION_REQUIRED → DRAFT` with `SAVE_REVISION`. The user chose to preserve audit and reject item removal: the frozen item is referenced by append-only creation history and has no removed state. Explicit remove requests return `PLAN_ITEM_RETENTION_CONFLICT`.

## 10. BR01 Enforcement

Only DRAFT and REVISION_REQUIRED are editable. SUBMITTED, APPROVED and later statuses return 409 `PLAN_NOT_EDITABLE`. A revised save must return DRAFT before another submit. Tests cover draft, revision, submitted and approved cases.

## 11. UC03 Submit Plan

The command requires PHONG_VTYT, current version, DRAFT status, at least one PLANNED item and valid dates. It changes state to SUBMITTED, creates one `PENDING` `PLAN_APPROVAL` request and writes `SUBMIT_PLAN` history atomically. Duplicate submit/pending request is rejected.

## 12. Approval Request Model

An `ApprovalRequest` represents one submission round and its pending/decided state. A separate immutable `ApprovalAction` represents a director's outcome. This separation preserves both rounds of a revision cycle without overwriting an old decision.

## 13. UC04 Director Decision

Only BAN_GIAM_DOC can decide a pending PLAN_APPROVAL request for a SUBMITTED plan. `APPROVE` changes the plan to APPROVED. `REVISION_REQUIRED` changes it to REVISION_REQUIRED and requires a nonblank reason. The service atomically creates an action, resolves the request and writes plan history. Wrong type, decided request, wrong plan state and duplicate decision are rejected.

## 14. Revision Loop

An integration test executes `DRAFT → SUBMITTED → REVISION_REQUIRED → DRAFT → SUBMITTED → APPROVED`. It verifies two distinct requests, two immutable actions, the complete six-state history chain, actor roles and removal from the pending queue after each decision. The Phase 1.3 DS-03 scenario informed this test.

## 15. Authorization

PHONG_VTYT alone may create, edit and submit. BAN_GIAM_DOC alone may decide. KHOA_PHONG and ADMIN have no planning/decision privilege. Spring Security restricts command paths and both services repeat the role check.

## 16. CurrentUser / Actor Identity

`CurrentUser` supplies the authenticated account ID and role. Creator, submitter, director actor and history actor are never accepted from request bodies. The JWT filter reloads the account on each request, preserving the Phase 2.6 identity behavior.

## 17. Audit / StatusHistory

Creation uses null old state. DRAFT edits use `DRAFT → DRAFT` to record UC02 changes, and the four implemented transitions use exact old/new values. Revision comment becomes the history reason. History rows are written with state changes in one transaction. No history or action mutation endpoint exists.

## 18. Transaction Boundaries

Each of `create`, `edit`, `submit` and `decide` is a service-level `@Transactional` command. Integration tests verify rollback for a duplicate item after an earlier insert, and verify stale/invalid commands leave no partial request/action/history. Repositories retain focused persistence duties; controllers do not coordinate multi-row writes.

## 19. Optimistic Locking

Edit, submit and decision require an expected `MaintenancePlan.version`. The service returns 409 `OPTIMISTIC_LOCK_CONFLICT` for a stale precondition; Hibernate `@Version` guards concurrent commits. Item-only edits also advance plan version. A stale-version test confirms no pending request or transition history is added. Optimistic locking prevents a later write from silently overwriting a newer plan.

## 20. Business Errors

Safe `ErrorResponse` codes cover invalid dates/input, missing plan/equipment/request, forbidden actor, noneditable/non-submittable plan, pending/decided/wrong-type approval, wrong plan state, audited item removal and stale version. Database conflict fallback remains `DATA_CONFLICT`; clients receive no SQL or entity internals.

## 21. Command API

| Method | Path | Actor | Purpose |
| --- | --- | --- | --- |
| POST | `/api/plans` | PHONG_VTYT | Create draft; 201 |
| PATCH | `/api/plans/{id}` | PHONG_VTYT | Additive edit; 200 |
| POST | `/api/plans/{id}/submit` | PHONG_VTYT | Submit round; 200 |
| POST | `/api/approvals/{requestId}/decision` | BAN_GIAM_DOC | Approve/revise; 200 |

Success returns small command DTOs, never entities. Existing GET routes continue to show new state/version and pending queue changes.

## 22. Integration Tests

Eight new methods in `PlanningApprovalIntegrationTest` exercise full HTTP/JWT commands against real PostgreSQL, including creation, edit, revision loop, pending queue, invalid input, duplicate commands, actor audit, rollback and version behavior. New result: **8/8 passed**. No H2 or Testcontainers was added.

## 23. Authorization Tests

Anonymous create returns 401. Director, department user and admin cannot create; department user cannot edit; admin cannot submit; all three non-director roles cannot decide. A direct service call with KHOA_PHONG also fails, demonstrating authorization beyond URL rules.

## 24. Transaction Rollback Tests

The create rollback test sends the same equipment twice. The plan and first item/history have already been persisted in the transaction when the second duplicate is rejected. A query by unique test title finds zero plan rows afterward. Stale submit and invalid decision tests confirm no partial request/action rows.

## 25. Revision Round Test

The end-to-end test finds two DECIDED requests and two actions (`REVISION_REQUIRED`, `APPROVE`) for its plan. It checks exact state order, revision reason and role of each transition actor.

## 26. Phase 2 Regression

The 36 pre-existing foundation/persistence/repository/API/security tests still pass. Total backend suite after Phase 3.1: **44 tests, 0 failures, 0 errors, 0 skips**. `mvn clean test` and independent `mvn clean package` passed after a clean database rebuild.

## 27. Database Regression

Only the isolated `medical_maintenance_backend_dev` was reset. Flyway reapplied unchanged V001–V006; Phase 1.3 seeds and four demo login hashes were loaded. After tests and JAR smoke cleanup, metadata remained **14 business tables / 117 columns / 31 FKs / six migrations / 603 business rows**, with zero temporary test/smoke plans. The separate Phase 1 DB was untouched.

## 28. Problems Found

Phase 2 JPA entities used protected no-argument constructors, so the new service package could not instantiate command records. A draft edit using `OPTIMISTIC_FORCE_INCREMENT` returned the pre-commit version in HTTP, causing the next valid submit to appear stale. The frozen schema cannot remove an item while retaining its append-only creation history. The project tree was already untracked in Git, so ordinary `git diff` did not provide a per-file change view; source files, reports, schema counts and source digests were inspected directly.

## 29. Fixes Applied

Five command-created entity constructors were made public without changing mappings. Draft edits use normal dirty-field version increments; item-only/no-metadata edits use forced optimistic increment and return the resulting version. The integration suite verifies database/response parity. Audited removal returns a named conflict under the user's chosen policy; no destructive history deletion or schema change was made.

## 30. What Is Not Implemented Yet

UC05–UC12, provider assignment, execution, acceptance, report commands, frontend work and later plan transitions remain outside Phase 3.1. No general item removal or generic status endpoint exists. Department scope for later use cases remains future work.

## 31. Phase 3.2 Handoff

Implement coverage/provider selection and assignment (UC05–UC07) over the existing entities and approval conventions. Keep `UNKNOWN` distinct from NOT_FREE, require verified routing evidence and director approval where appropriate, preserve audit/history, and maintain transaction/role/version safeguards. Do not add a migration without a reviewed design change.

## 32. Final Status

**PASS** for the authorized audit-preserving scope: UC01–UC04 work with role/state/version checks, atomic approval rounds and history. All 44 backend tests and packaged JAR smoke pass; the frozen schema and 603-row seed are intact. Item removal is explicitly rejected as agreed, pending a future reviewed design if required.

## 33. Slide-ready Summary

- Phase 3.1 adds four named commands for UC01–UC04 over the frozen schema.
- VTYT drafts/edits/submits; BGĐ decides; other roles cannot issue these commands.
- A revision creates a second approval round without replacing the first decision.
- Plan state, approval evidence and status history commit together.
- Stale edits and duplicate commands return safe 409 errors.
- 44/44 tests, clean rebuild, packaged JAR smoke and 14/117/31/603 database regression pass.
