# Reporting & Equipment History

Phase 3.4 completes UC11–UC12 on the frozen V1 database. The source is `docs/system_analysis_v1.pdf` and the Phase 1.2 [implementation contract](../../database_design/phase_1_2_implementation_contract.md). No new table, migration, status or repair workflow was added.

## UC11 Reporting

PHONG_VTYT can create one report draft for a plan, edit it while DRAFT, and finalize it. BAN_GIAM_DOC may read the report. The report uses only the frozen BM03 fields in `maintenance_report`; report date and finalization timestamp are set by the server.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/plans/{planId}/report` | Create DRAFT; 201 |
| PUT | `/api/plans/{planId}/report` | Replace draft narrative; 200 |
| POST | `/api/plans/{planId}/report/finalize` | Finalize; 200 |
| GET | `/api/plans/{planId}/report` | Read flat DTO; 200 |

The create/edit body has `version` plus `reportNumber`, `workDone`, `achieved`, `notAchieved`, `causes`, `nextWork`, `resolutions`, and `recommendations`. Finalize takes `version`. Actor identity and dates are never accepted from the client.

## Report Eligibility

The plan must be `AWAITING_REPORT` and have at least one item. Every item must be `COMPLETED` or `REPAIR_REQUIRED`. A plan in any earlier or later state is rejected. The service checks both plan state and item outcomes; it does not trust either alone. A unique database key and service check enforce one report per plan.

## Report Lifecycle

Create/edit keep the report `DRAFT` and the plan `AWAITING_REPORT`. These writes advance the plan version so concurrent draft edits cannot silently replace one another. A FINAL report requires nonblank `workDone`; it cannot be overwritten or deleted through the API. There is no report version column in the frozen schema, so the plan's version is the draft/final command token.

## Outcome Counts

The response computes `completedCount` and `repairRequiredCount` from current plan items. The request cannot supply counts, and no redundant counter column is stored. `REPAIR_REQUIRED` remains a terminal V1 hand-off that must be reported separately from `COMPLETED`.

## Plan Report Transition

Finalization atomically changes report `DRAFT → FINAL`, plan `AWAITING_REPORT → REPORTED`, and appends one plan `StatusHistory` event. A test-induced history failure rolls back all three. The source state diagram includes `REPORTED → CLOSED`, but UC11 gives no actor or closing condition; Phase 3.4 therefore ends at `REPORTED` and does not invent a close command.

## UC12 Equipment History

`GET /api/equipment/{equipmentId}/maintenance-history` returns a read-only hierarchical DTO. It contains equipment identity; ordered campaigns; plan/item state and historical department; route and allowed provider references; numbered executions; chronological progress; technical and handover assessments, including failed attempts; item/plan status history; and a concise report reference. There is no entity serialization, report-body duplication or edit/delete operation.

## History Read Model

The read model aggregates existing rows; it creates no persistence table. Campaigns are ordered newest first by plan creation time, plan ID and item ID. Attempts are ordered by attempt number. Progress and status events are ordered by timestamp then ID. A typed acceptance stays on its own execution, so an old PASS cannot be mistaken for the current attempt.

## Multi-campaign History

One equipment can appear in several plans. The response returns all campaigns visible to the actor, including the Phase 1.3 DS-09 device with an earlier completed campaign and a later repair hand-off. No campaigns are collapsed into the current item status.

## Multi-attempt History

Each rework attempt appears separately with its own provider, logs and assessments. Technical FAIL, handover FAIL and later PASS records remain visible. The report reference is only ID, status and dates; it does not embed the whole report narrative.

## Department Scope

PHONG_VTYT and BAN_GIAM_DOC can read broad maintenance history. ADMIN has no UC12 business-history privilege. KHOA_PHONG can access equipment under current custody or with at least one campaign whose `department_id_at_plan` matches its authenticated department. The service returns only matching historical campaigns. A former department may still see its own old campaign after equipment moves; the new department does not inherit old campaigns. Current custody is masked for a former department. Khoa/Phòng also receives no coverage ID, assigned-provider reference, plan-wide history or unfinished report reference from UC12.

Existing equipment and plan GET paths now scope KHOA_PHONG results: equipment search includes current or historical custody, with current department details masked for former custodians; plan list/detail/items expose only plans/items with the actor's historical department. BGD/VTYT read behavior remains as before.

## Query Strategy

The service loads the equipment, visible items, and then uses separate `IN` batch queries for executions, progress, acceptances, item history, plan history and report references. It uses to-one fetches for plan/provider data and no global EAGER association or Cartesian mega-join. Actual authenticated HTTP checks on the 603-row fixture used 7 SQL for a simple history and 9 for a multi-campaign history; the multi-attempt case is also measured in the final test. These are bounded demo-data query shapes, not production latency proof.

## Business Errors

Commands reuse the existing `ErrorResponse` shape. Typical codes: `PLAN_NOT_REPORTABLE`, `REPORT_ALREADY_EXISTS`, `REPORT_NOT_FOUND`, `REPORT_NOT_EDITABLE`, `REPORT_NOT_FINALIZABLE`, `REPORT_WORK_DONE_REQUIRED`, `OPTIMISTIC_LOCK_CONFLICT`, `EQUIPMENT_NOT_FOUND`, `EQUIPMENT_HISTORY_ACCESS_DENIED`, and `DEPARTMENT_SCOPE_VIOLATION`. Missing records are 404, invalid text is 400, wrong role/scope 403, and state/version/duplicate conflicts 409.

## Final Backend Business Scope

UC01–UC12 and BR01–BR05 have backend V1 paths. The reporting workflow stops at `REPORTED`; `CLOSED` remains a documented state without an invented administrative command. Frontend, V2 repair, attachments, notifications, contract finance, refresh tokens and formal production-scale performance testing remain outside the backend business freeze.
