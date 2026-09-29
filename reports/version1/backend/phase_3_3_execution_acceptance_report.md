# Phase 3.3 — Execution, Technical Acceptance & Handover

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-26  
**Status:** **PASS**

## 1. Objective

Implement UC08–UC10 on the frozen V1 schema: actual maintenance attempts, progress evidence, technical acceptance, handover, rework and terminal repair hand-off.

## 2. Starting Point

Phase 3.2 ended with routed `UNDER_CONTRACT` and `ASSIGNED_EXTERNAL` items. Phase 2 supplied JWT identity, DTO/error conventions, 14 mapped entities, PostgreSQL and Flyway. No UC08–UC10 command existed.

## 3. BR02 / BR03 / BR04 / BR05

BR02 gates work on an APPROVED/IN_PROGRESS plan and valid route. BR03 ties actual execution provider to FREE coverage or approved external selection. BR04 requires technical PASS on the current attempt and two scoped signers before completion. BR05 records every plan/item transition in the same transaction.

## 4. Execution Model

`maintenance_execution` stores one numbered attempt and its actual provider. `maintenance_progress_log` stores append-only work events. `acceptance_record` stores at most one technical and one handover assessment per attempt, protected by frozen unique constraints.

## 5. What Is an Execution Attempt?

An attempt is one run of work on a plan item. If an assessment fails, the next run receives a new row and the next attempt number. This keeps failed work, notes and assessments visible rather than rewriting them.

## 6. UC08 Start Maintenance

`POST /api/plan-items/{id}/executions` takes the current item and plan versions. PHONG_VTYT starts only routed or rework items. The service locks plan then item, validates evidence, derives the provider, creates the next attempt and appends item history.

## 7. Provider Evidence Revalidation

Coverage must still match equipment/date and have VTYT verification, time and basis. FREE route provider must equal coverage provider. External route provider must equal the latest decided APPROVE vendor request provider. Inactive or mismatched providers, UNKNOWN and missing evidence fail safely.

## 8. Plan APPROVED → IN_PROGRESS

The first start atomically changes plan status and writes one plan history row. Later item starts keep `IN_PROGRESS` without a same-state history event. Plan row locking serializes first starts on different items.

## 9. Progress Logging

`POST /api/executions/{id}/progress` appends a timestamped nonblank work note and optional damage note to the current open attempt. Recorded user comes from JWT identity. No edit/delete endpoint exists.

## 10. Finish Work

`POST /api/executions/{id}/complete-work` sets the current attempt's end time and optional result note, then moves the item to `AWAITING_TECHNICAL_ACCEPTANCE`. It does not claim technical PASS.

## 11. REPAIR_REQUIRED

Active work can be handed off with a required reason; the transaction adds a damage progress log, ends work and records `REPAIR_REQUIRED` history. Technical or handover FAIL can also select repair. V1 stops there; it does not create a repair order.

## 12. UC09 Technical Acceptance

Only PHONG_VTYT can assess the latest ended attempt of an item awaiting technical acceptance. The command requires item version, PASS/FAIL and nonblank conclusion. A duplicate type on the attempt receives 409.

## 13. Technical PASS

PASS creates a typed assessment and moves the item to `AWAITING_HANDOVER`. It cannot simultaneously request repair.

## 14. Technical FAIL

FAIL retains the assessment and moves to `REWORK_REQUIRED`, or explicitly to `REPAIR_REQUIRED` with the conclusion as the required transition reason.

## 15. Rework Attempts

`REWORK_REQUIRED` starts a new `maintenance_execution` row with the next ordinal. The previous attempt, progress and failed acceptance remain unchanged. Routing evidence is checked again.

## 16. UC10 Handover

`POST /api/executions/{id}/handover` is initiated by KHOA_PHONG after current-attempt technical PASS. The receiving actor must belong to `department_id_at_plan`, independent of the equipment's present department.

## 17. Department Scope

The primary Bearer session supplies the Khoa/Phòng signer. For PASS, `X-VTYT-Authorization` supplies a second signed Bearer session; it is verified and the active account is reloaded as PHONG_VTYT. Neither signer ID is accepted from the body.

## 18. Handover PASS

Both signer IDs and server timestamps are populated before inserting the PASS record, satisfying the frozen database constraint. The item then moves to `COMPLETED`; the acceptance, status and audit row commit together.

## 19. Handover FAIL

A failed department inspection creates FAIL evidence and moves to `REWORK_REQUIRED` or explicit `REPAIR_REQUIRED`. It never completes the item. A rework loop starts a new attempt.

## 20. COMPLETED Rule

Only handover PASS on the latest execution with technical PASS on that same execution can produce `COMPLETED`. Old attempt PASS cannot satisfy a later attempt.

## 21. Multi-attempt Preservation

Integration scenarios retained both technical-failure and handover-failure attempts. No historical execution, progress or acceptance update/delete route was added.

## 22. Plan Completion / AWAITING_REPORT Decision

UC10 step 10 and the frozen design decision define the trigger. When every item is `COMPLETED` or `REPAIR_REQUIRED`, the transaction moves the plan from `IN_PROGRESS` to `AWAITING_REPORT` and appends one plan history row. Repair remains separately reportable, never counted as completed.

## 23. Role Authorization

PHONG_VTYT owns start, progress, finish, repair hand-off and technical assessment. KHOA_PHONG owns the scoped handover command. BAN_GIAM_DOC and ADMIN cannot invoke them. URL matchers and service checks both enforce command roles; vendor identities remain data only.

## 24. Transactions

Each command has a service-level `@Transactional` boundary. One test deliberately throws during technical status-history insertion after the acceptance insert; the assessment and state both roll back.

## 25. StatusHistory

Every changed plan/item status gets old/new state, action, actor and timestamp. REWORK_REQUIRED and REPAIR_REQUIRED include a nonblank reason. No same-state plan event is inserted for later starts.

## 26. Optimistic Locking

All item state commands require expected item version. Start also requires plan version. Stale inputs return 409 without partial work. Plan-then-item row locks serialize starts and final outcome checks; the frozen attempt and acceptance unique constraints remain final race guards.

## 27. Command API

| Method/path | Purpose | Success |
| --- | --- | ---: |
| POST `/api/plan-items/{id}/executions` | Start attempt | 201 |
| POST `/api/executions/{id}/progress` | Append progress | 201 |
| POST `/api/executions/{id}/complete-work` | Close work | 200 |
| POST `/api/executions/{id}/repair-required` | Repair hand-off | 200 |
| POST `/api/executions/{id}/technical-acceptance` | Technical PASS/FAIL | 201 |
| POST `/api/executions/{id}/handover` | Scoped handover PASS/FAIL | 201 |

Responses are flat DTOs, never JPA entities.

## 28. Business Errors

The existing structured error JSON is reused. Invalid syntax is 400; missing actor 401; wrong role or department 403; missing resource 404; stale version, route/state/evidence or duplicate assessment 409. Signer token errors are safe and do not expose JWT/parser details.

## 29. Integration Tests

The new `ExecutionAcceptanceIntegrationTest` has **13 tests, 0 failures** on PostgreSQL 16. It covers contract/external starts, progress, finish, technical/handover success and failure, route tampering, plan transition, roles, department and signer checks, stale versions and history.

## 30. Multi-attempt Tests

DS-04 style technical rework preserves attempt 1 and starts attempt 2. DS-05 style handover rework reaches later completion. DS-06 style active damage becomes `REPAIR_REQUIRED`. A technical PASS on a prior attempt cannot authorize a later handover.

## 31. Rollback Tests

A test-only WorkflowHistory spy fails after a technical acceptance would be written. The transaction leaves no acceptance, status change or extra history. Test fixtures clean their own rows after each test.

## 32. Authorization / Department Tests

Anonymous and wrong-role calls are rejected. A Khoa/Phòng account from another department is rejected against the item's historical department. Missing or non-VTYT second session cannot sign a PASS handover.

## 33. Phase 3.2 Regression

**14/14** provider-routing tests pass, including FREE, NOT_FREE, UNKNOWN, approval rounds and stale versions.

## 34. Phase 3.1 Regression

**8/8** planning/approval tests pass, including BR01, plan approvals, revision rounds and retention.

## 35. Phase 2 Regression

**36/36** foundation, persistence, repository, API and security tests pass. Full backend regression after clean rebuild and independent package is **71/71**, zero failures/errors/skips.

## 36. Database Regression

The isolated dev DB was reset, migrated by unchanged V001–V006, seeded with 603 canonical rows and configured with four demo logins. Final metadata: **14 business tables, 117 columns, 31 FKs, 6 successful migrations, 603 business rows**. No V007 or schema/seed edit. A packaged-JAR smoke completed UC08–UC10 with two authenticated signers; its records were removed. The JAR log had no ERROR entries or visible Bearer tokens.

## 37. Problems Found

The first handover implementation attempted to persist a PASS acceptance before setting both signer fields. PostgreSQL correctly rejected it under `ck_acceptance_handover_signers`. A client-supplied VTYT user ID would also have been insufficient evidence of that person's confirmation.

## 38. Fixes Applied

Acceptance fields are now populated before the identity insert. Handover PASS requires a second authenticated VTYT Bearer session, rechecks account activity/role and derives the signer ID from it. Tests verify missing and wrong-role confirmation are rejected.

## 39. What Is Not Implemented Yet

UC11 report generation, UC12 equipment-history HTTP presentation, frontend and V2 repair workflow remain outside this phase. No general attachment, inventory, notification or workflow-engine feature was added.

## 40. Phase 3.4 Handoff

Use terminal items and the `AWAITING_REPORT` plan state for UC11. Preserve separate counts for COMPLETED and REPAIR_REQUIRED; retain failed attempts in UC12 history. The [workflow guide](../../backend/docs/execution-acceptance-workflow.md) documents commands and status edges.

## 41. Final Status

**PASS.** UC08–UC10, BR02–BR05, attempt and acceptance integrity, department scope, transaction rollback, clean rebuild, JAR smoke and all 71 backend tests pass.

## 42. Slide-ready Summary

- Phase 3.3 PASS: UC08–UC10 implemented on the frozen schema.
- Verified FREE/approved external evidence determines the actual execution provider.
- Rework creates a new numbered attempt; failed records remain.
- Technical PASS and two authenticated scoped handover signers are required for COMPLETED.
- Repair hand-off is terminal V1; all final outcomes move the plan to AWAITING_REPORT.
- 13/13 new tests, 71/71 full tests and packaged-JAR workflow smoke pass.
- Database remains 14 tables / 117 columns / 31 FKs, V001–V006 and 603 demo rows.
