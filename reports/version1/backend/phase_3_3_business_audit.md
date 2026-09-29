# Phase 3.3 — Business Audit

**Date:** 2026-09-26  
**Result:** **PASS**  
**Evidence:** `ExecutionAcceptanceIntegrationTest` (13/13), full backend suite (71/71), clean rebuild, packaged-JAR workflow smoke. Rules are grounded in `docs/system_analysis_v1.pdf`, the frozen [implementation contract](../../database_design/phase_1_2_implementation_contract.md), [dictionary](../../database_design/data_dictionary.md), [constraints](../../database_design/database_constraints.md), and [design decisions](../../database_design/design_decisions.md).

## UC Coverage

| UC | Main commands | Result |
| --- | --- | --- |
| UC08 | Start numbered attempt, append progress, finish work, repair hand-off | PASS |
| UC09 | Current-attempt technical PASS/FAIL and rework/repair choice | PASS |
| UC10 | Scoped two-party handover PASS/FAIL and plan report readiness | PASS |

## Execution Eligibility Audit

| Item route | Evidence | Expected | Result |
| --- | --- | --- | --- |
| UNDER_CONTRACT | Verified applicable FREE coverage, active matching provider | Start; execution provider = coverage provider | PASS |
| ASSIGNED_EXTERNAL | Verified NOT_FREE coverage, latest decided approved vendor request, active matching provider | Start; execution provider = approved provider | PASS |
| REWORK_REQUIRED | Prior ended attempt and revalidated route | New numbered attempt | PASS |
| PLANNED / pending vendor | No finalized route | 409, no attempt | PASS by state gate |
| Routed state with changed approval/provider evidence | Mismatch | 409, no attempt | PASS |
| Any route in non-operational plan | Plan not APPROVED/IN_PROGRESS | 409 | PASS by state gate |
| REPAIR_REQUIRED | Terminal V1 | No new attempt | PASS |

## Item Transition Audit

| From | Command/result | To | Result |
| --- | --- | --- | --- |
| UNDER_CONTRACT / ASSIGNED_EXTERNAL | Start | IN_MAINTENANCE | PASS |
| REWORK_REQUIRED | New attempt | IN_MAINTENANCE | PASS |
| IN_MAINTENANCE | Finish | AWAITING_TECHNICAL_ACCEPTANCE | PASS |
| IN_MAINTENANCE | Damage hand-off | REPAIR_REQUIRED | PASS |
| AWAITING_TECHNICAL_ACCEPTANCE | Technical PASS | AWAITING_HANDOVER | PASS |
| AWAITING_TECHNICAL_ACCEPTANCE | Technical FAIL | REWORK_REQUIRED or REPAIR_REQUIRED | PASS |
| AWAITING_HANDOVER | Handover PASS | COMPLETED | PASS |
| AWAITING_HANDOVER | Handover FAIL | REWORK_REQUIRED or REPAIR_REQUIRED | PASS |

Plan transitions: first start `APPROVED → IN_PROGRESS`; all items terminal `IN_PROGRESS → AWAITING_REPORT`. Subsequent starts insert no duplicate plan transition. Each transition has one append-only history row.

## Attempt Audit

| Scenario | Attempts expected | Actual | Result |
| --- | ---: | ---: | --- |
| Contract route, first work | 1 | 1 | PASS |
| External route, first work | 1 | 1 | PASS |
| Technical FAIL then rework | 2, old attempt preserved | 2 | PASS |
| Handover FAIL then rework | 2, old attempt preserved | 2 | PASS |
| Active damage hand-off | 1, terminal | 1 | PASS |
| Old attempt operation after rework | Rejected | 409 | PASS |

The frozen `UNIQUE(plan_item_id, attempt_no)` is retained. Plan-then-item locking serializes number selection for an item's plan.

## Technical Acceptance Audit

| Scenario | Result | Item state |
| --- | --- | --- |
| Current ended attempt PASS | Saved once | AWAITING_HANDOVER |
| Current ended attempt FAIL/rework | Saved once | REWORK_REQUIRED |
| Current ended attempt FAIL/repair | Saved once | REPAIR_REQUIRED |
| Before work ends | 409 | IN_MAINTENANCE |
| Duplicate technical assessment | 409 | Unchanged |
| Old attempt after rework | 409 | Unchanged |
| Stale item version | 409 | Unchanged |

## Handover Audit

| Scenario | Result | Item state |
| --- | --- | --- |
| Current technical PASS, scoped Khoa/Phòng, authenticated VTYT confirmer | PASS saved with both signer IDs/times | COMPLETED |
| Technical PASS absent on current attempt | 409 | Unchanged |
| Department inspection FAIL/rework | FAIL retained | REWORK_REQUIRED |
| Department inspection FAIL/repair | FAIL retained | REPAIR_REQUIRED |
| Missing second session for PASS | 400 | AWAITING_HANDOVER |
| Non-VTYT second session | 403 | AWAITING_HANDOVER |
| Duplicate handover acceptance | 409 | Unchanged |
| Old technical PASS on an earlier attempt | 409 | Unchanged |

The frozen `UNIQUE(execution_id, acceptance_type)` and `ck_acceptance_handover_signers` remain active.

## Department Scope Audit

| Actor department | Item historical department | Expected | Result |
| --- | --- | --- | --- |
| KHOA_NOI | KHOA_NOI | Permit handover when other rules pass | PASS |
| Other department | KHOA_NOI | 403, no acceptance/state change | PASS |
| Current equipment department differs | Historical item department | Use item snapshot FK | PASS in integration test |

## Role Audit

| Command | PHONG_VTYT | BAN_GIAM_DOC | KHOA_PHONG | ADMIN |
| --- | --- | --- | --- | --- |
| Start execution | Allow | 403 | 403 | 403 |
| Add progress | Allow | 403 | 403 | 403 |
| Finish work | Allow | 403 | 403 | 403 |
| Repair hand-off | Allow | 403 | 403 | 403 |
| Technical acceptance | Allow | 403 | 403 | 403 |
| Handover initiator | 403 | 403 | Allow if scoped | 403 |
| Handover PASS confirmer | Allow as second authenticated signer | Reject | Reject | Reject |

URL rules and service role checks both apply. The second signer token is verified in a security component and its account is reloaded.

## Transaction Audit

A test-only failure in `WorkflowHistory.itemTransition` after creating a technical acceptance returned a safe 500 and rolled back the acceptance, item state and history. Start and terminal transitions are single service transactions; plan and item history are written with their state changes. No official evidence DELETE endpoint exists.

## Optimistic Lock Audit

| Command | Version precondition | Stale result |
| --- | --- | --- |
| Start attempt | Item + plan | 409, no attempt or plan transition |
| Finish work | Item | 409, no end/state change |
| Technical acceptance | Item | 409, no assessment |
| Handover | Item | 409, no assessment |
| Repair hand-off | Item | 409, no damage/state change |

Plan and item row locks are acquired in the same order, with a refreshed plan version before processing. The concurrent two-item start behavior is reasoned from this locking order and verified sequentially for a single plan transition; no multi-thread load benchmark was claimed.

## History Audit

Verified exact item state chains for a complete contract flow, technical rework, handover rework and repair hand-off. REWORK_REQUIRED/REPAIR_REQUIRED reasons are nonblank. The plan records exactly one first-start history and one report-readiness history when all items are terminal.

## Regression Summary

| Check | Result |
| --- | --- |
| Phase 3.3 new integration tests | 13/13 PASS |
| Phase 3.2 regression | 14/14 PASS |
| Phase 3.1 regression | 8/8 PASS |
| Phase 2 regression | 36/36 PASS |
| Full clean test and independent package | 71/71 PASS, 0 failures/errors/skips |
| Packaged-JAR UC08–UC10 smoke | PASS, two authenticated handover sessions |
| Frozen database | 14 tables / 117 columns / 31 FKs / V001–V006 |
| Canonical seed after cleanup | 603 business rows; 0 smoke plans |
