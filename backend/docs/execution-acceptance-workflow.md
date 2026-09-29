# Execution, Technical Acceptance & Handover

Phase 3.3 implements UC08–UC10 against the frozen 14-table schema. All timestamps are generated in UTC, and commands return flat DTOs. No vendor login, repair work order, report, or equipment-history HTTP route is included.

## UC08 Start Maintenance

`POST /api/plan-items/{itemId}/executions` accepts `version` and `planVersion`. A PHONG_VTYT actor may start only an `UNDER_CONTRACT` or `ASSIGNED_EXTERNAL` item in an `APPROVED` or `IN_PROGRESS` plan. The first start changes the plan to `IN_PROGRESS`; later starts leave the plan state alone.

## Provider Evidence Revalidation

At each start, coverage must match the equipment and planned date and contain VTYT verification, time and basis. FREE coverage must match the assigned active contract provider. NOT_FREE coverage must have an approved latest submitted vendor round whose proposed provider matches the active assigned provider. `UNKNOWN`, absent evidence and a changed approval block work. The execution provider is derived from the validated assignment.

## Execution Attempts

Each start creates one numbered `MaintenanceExecution` row. After rework, the previous ended attempt remains and a new row gets `max(attempt_no)+1`. The plan and item are locked in a consistent order during start, and the frozen unique key on `(plan_item_id, attempt_no)` is a final guard. `REPAIR_REQUIRED` is terminal in V1.

## Progress Logs

`POST /api/executions/{id}/progress` appends an immutable log to the current active attempt. The actor and event time come from the server. It accepts a nonblank work note and optional damage note. Old logs cannot be edited or deleted by this API.

## Finish Work

`POST /api/executions/{id}/complete-work` accepts the item version and optional result note. It closes the current attempt and moves `IN_MAINTENANCE → AWAITING_TECHNICAL_ACCEPTANCE`; it does not imply a successful assessment.

## REPAIR_REQUIRED

`POST /api/executions/{id}/repair-required` requires a reason while work is active. It appends damage progress, closes the attempt, moves the item to `REPAIR_REQUIRED`, and writes history in one transaction. A technical or handover FAIL may also select `repairRequired=true`; the nonblank assessment conclusion is the reason. V2 repair remains outside this API.

## UC09 Technical Acceptance

`POST /api/executions/{id}/technical-acceptance` requires PHONG_VTYT, item version, result `PASS` or `FAIL`, and a nonblank conclusion. Only the latest ended attempt in `AWAITING_TECHNICAL_ACCEPTANCE` qualifies. The frozen unique key allows one technical record per attempt.

## Technical PASS

A PASS creates `TECHNICAL_ACCEPTANCE` and moves the item to `AWAITING_HANDOVER`. It cannot simultaneously request repair.

## Technical FAIL / Rework

A FAIL creates an immutable assessment and moves to `REWORK_REQUIRED`, or to `REPAIR_REQUIRED` when the command explicitly identifies repair. Rework starts a fresh execution; the failed assessment stays attached to the old attempt.

## UC10 Handover

`POST /api/executions/{id}/handover` is initiated by an authenticated KHOA_PHONG user. The item must be `AWAITING_HANDOVER` and the latest execution must have its own technical PASS. The body supplies item version, result, conclusion and optional repair choice.

## Department Scope

The receiving actor's current department must equal `maintenance_plan_item.department_id_at_plan`, preserving the historical department even if equipment moves. For PASS, a second active PHONG_VTYT user must authenticate through `X-VTYT-Authorization: Bearer <token>`. The service derives both signer IDs from authenticated sessions. It does not trust client-supplied signer IDs. The header must be sent only over a protected transport in a deployed environment.

## Handover PASS

The Khoa/Phòng signer and VTYT confirmer, with their server timestamps, are stored on the `HANDOVER_ACCEPTANCE` record before it is inserted. The VTYT user is also the record's recorder. Only then does the item become `COMPLETED`.

## Handover FAIL / Rework

A FAIL creates a failed handover record and moves to `REWORK_REQUIRED`, or `REPAIR_REQUIRED` if repair is explicitly selected. The Khoa/Phòng actor records a failed inspection; successful two-party sign-off is not asserted. A new maintenance start is required after rework.

## Multi-attempt Workflow

```text
UNDER_CONTRACT / ASSIGNED_EXTERNAL
            ↓ start attempt
      IN_MAINTENANCE ── damage → REPAIR_REQUIRED
            ↓ finish
AWAITING_TECHNICAL_ACCEPTANCE
       PASS ↓       ↓ FAIL
AWAITING_HANDOVER   REWORK_REQUIRED ── new attempt → IN_MAINTENANCE
       PASS ↓       ↑ FAIL
       COMPLETED
```

Assessments always target the current/latest execution. A PASS on attempt 1 cannot validate attempt 2.

## Status Transitions

UC08: routed/rework → `IN_MAINTENANCE` → `AWAITING_TECHNICAL_ACCEPTANCE` or `REPAIR_REQUIRED`. UC09: `AWAITING_TECHNICAL_ACCEPTANCE` → `AWAITING_HANDOVER`, `REWORK_REQUIRED`, or `REPAIR_REQUIRED`. UC10: `AWAITING_HANDOVER` → `COMPLETED`, `REWORK_REQUIRED`, or `REPAIR_REQUIRED`. First work changes plan `APPROVED → IN_PROGRESS`. Once every item is `COMPLETED` or `REPAIR_REQUIRED`, the plan changes `IN_PROGRESS → AWAITING_REPORT`. `REPAIR_REQUIRED` remains separately reportable, never counted as completed.

## Transaction Boundaries

Each command is a service-level transaction. Attempt, progress, acceptance, state and StatusHistory writes commit together. A deliberate history failure test verifies rollback of a newly inserted acceptance. The API offers no DELETE or UPDATE for official attempt/log/acceptance/history evidence.

## Optimistic Locking

State-changing commands require the current item version. Start also requires the plan version. The service locks the plan then item to serialize concurrent starts and final-item transitions within a plan; stale versions return `409 OPTIMISTIC_LOCK_CONFLICT`. The database unique keys guard duplicate attempts and typed assessments.

## Business Error Codes

Representative safe codes: `PLAN_NOT_EXECUTABLE`, `PLAN_ITEM_NOT_EXECUTABLE`, `ROUTING_EVIDENCE_MISSING`, `INVALID_PROVIDER_ROUTE`, `EXECUTION_NOT_CURRENT`, `EXECUTION_NOT_ACTIVE`, `EXECUTION_NOT_READY_FOR_ACCEPTANCE`, `TECHNICAL_ACCEPTANCE_EXISTS`, `TECHNICAL_ACCEPTANCE_REQUIRED`, `HANDOVER_NOT_ALLOWED`, `HANDOVER_ACCEPTANCE_EXISTS`, `HANDOVER_SIGNER_REQUIRED`, `HANDOVER_SIGNER_INVALID`, `DEPARTMENT_SCOPE_VIOLATION`, and `OPTIMISTIC_LOCK_CONFLICT`.

## What Is Deferred to Phase 3.4

UC11 report generation and UC12 equipment-history HTTP presentation remain for Phase 3.4. No Repair V2, frontend, generic workflow engine or database migration was added.
