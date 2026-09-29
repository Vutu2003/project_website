# Phase 3.4 — Full Business Audit

**Date:** 2026-09-26  
**Result:** **PASS**  
**Evidence:** clean PostgreSQL rebuild; 82/82 backend tests; final packaged-JAR HTTP smoke; final SQL invariant audit. Business source: `docs/system_analysis_v1.pdf`; storage authority: frozen [Phase 1.2 contract](../../database_design/phase_1_2_implementation_contract.md).

## UC Audit

| UC | Main capability | Status | Tests/evidence |
| --- | --- | --- | --- |
| UC01 | Create plan/items | PASS | Phase 3.1; full E2E |
| UC02 | Edit eligible plan; reject audited item removal | PASS | Phase 3.1 regression |
| UC03 | Submit plan | PASS | Phase 3.1; full E2E |
| UC04 | Plan director decision/revision | PASS | Phase 3.1; full E2E |
| UC05 | Verified FREE/NOT_FREE routing | PASS | Phase 3.2; full E2E |
| UC06 | Draft/submit vendor proposal | PASS | Phase 3.2; mixed-outcome E2E |
| UC07 | Vendor director decision/revision | PASS | Phase 3.2; mixed-outcome E2E |
| UC08 | Numbered execution/progress/repair hand-off | PASS | Phase 3.3; full E2E |
| UC09 | Technical PASS/FAIL and rework | PASS | Phase 3.3; failure-history E2E |
| UC10 | Scoped two-party handover | PASS | Phase 3.3; full E2E |
| UC11 | DRAFT/FINAL report and REPORTED plan | PASS | 11 Phase 3.4 tests; JAR smoke |
| UC12 | Scoped multi-campaign/attempt history | PASS | 11 Phase 3.4 tests; JAR smoke |

## BR Audit

| BR | Rule | Enforcement | Result |
| --- | --- | --- | --- |
| BR01 | Edit only DRAFT/REVISION_REQUIRED; revision save→DRAFT | PlanningService version/state guard; item removal conflict | PASS |
| BR02 | No work before approved plan and valid route | ExecutionAcceptanceService plan/item/evidence gate | PASS |
| BR03 | FREE contract provider; NOT_FREE approved external; UNKNOWN blocked | Assignment + execution revalidation; actual provider FK | PASS |
| BR04 | Current technical PASS and signed handover before COMPLETED | Current execution/typed acceptance/signers; frozen DB check | PASS |
| BR05 | Every plan/item transition has append-only history | Service transactions + WorkflowHistory; final mismatch queries 0/0 | PASS |

## Plan Transition Audit

| From | To | Command/trigger | Result |
| --- | --- | --- | --- |
| new | DRAFT | UC01 create | PASS |
| DRAFT | SUBMITTED | UC03 submit | PASS |
| SUBMITTED | APPROVED / REVISION_REQUIRED | UC04 director action | PASS |
| REVISION_REQUIRED | DRAFT | UC02 revision save | PASS |
| APPROVED | IN_PROGRESS | First UC08 start | PASS |
| IN_PROGRESS | AWAITING_REPORT | All items COMPLETED or REPAIR_REQUIRED | PASS |
| AWAITING_REPORT | REPORTED | UC11 finalization | PASS |
| REPORTED | CLOSED | Source diagram only; no actor/condition in UC11 | Deferred intentionally |

The final SQL audit found **0 plan current-status/history mismatches**. No generic status endpoint exists.

## Item Transition Audit

| From | To | Use case | Result |
| --- | --- | --- | --- |
| PLANNED | UNDER_CONTRACT / PENDING_PROPOSAL | UC05 | PASS |
| PENDING_PROPOSAL | WAITING_VENDOR_APPROVAL | UC06 | PASS |
| WAITING_VENDOR_APPROVAL | ASSIGNED_EXTERNAL / PENDING_PROPOSAL | UC07 | PASS |
| UNDER_CONTRACT / ASSIGNED_EXTERNAL / REWORK_REQUIRED | IN_MAINTENANCE | UC08 | PASS |
| IN_MAINTENANCE | AWAITING_TECHNICAL_ACCEPTANCE / REPAIR_REQUIRED | UC08 | PASS |
| AWAITING_TECHNICAL_ACCEPTANCE | AWAITING_HANDOVER / REWORK_REQUIRED / REPAIR_REQUIRED | UC09 | PASS |
| AWAITING_HANDOVER | COMPLETED / REWORK_REQUIRED / REPAIR_REQUIRED | UC10 | PASS |

The final SQL audit found **0 item current-status/history mismatches**. The seed retains **4 REPAIR_REQUIRED** items as non-COMPLETED V1 outcomes.

## Role Matrix

| Capability | PHONG_VTYT | BAN_GIAM_DOC | KHOA_PHONG | ADMIN |
| --- | --- | --- | --- | --- |
| UC01–03 plan commands | Allow | Deny | Deny | Deny |
| UC04 plan decision | Deny | Allow | Deny | Deny |
| UC05–06 route/vendor proposal | Allow | Deny | Deny | Deny |
| UC07 vendor decision | Deny | Allow | Deny | Deny |
| UC08 work/progress/repair | Allow | Deny | Deny | Deny |
| UC09 technical acceptance | Allow | Deny | Deny | Deny |
| UC10 handover initiator | Deny | Deny | Scoped allow | Deny |
| UC10 PASS VTYT confirmer | Second authenticated signer | Deny | Deny | Deny |
| UC11 report write | Allow | Deny | Deny | Deny |
| UC11 report read | Allow | Allow | Deny | Deny |
| UC12 history read | Broad allow | Broad allow | Scoped allow | Deny |

Security route matchers and service checks agree on UC11–UC12. Older generic Phase 2 authenticated reads retain their accepted ADMIN behavior; that is documented as a production policy review item, not a grant of UC12/report privilege.

## Department Scope Matrix

| Read/action | Khoa/Phòng rule | Tested result |
| --- | --- | --- |
| UC10 handover | Item historical department must match actor | Wrong department 403 |
| UC12 equipment history | Current custody or matching historical campaign for access | Wrong equipment 403 |
| UC12 campaign rows | Return only matching `department_id_at_plan` | Cross-department rows absent |
| Former department after transfer | Preserve old campaign, mask current custody | PASS |
| Equipment list/detail | Current or historical custody; mask current department if former | PASS |
| Plan list/detail/items | Plan must include matching historical item; return only matching items | PASS |
| UC12 report/coverage/plan history | Final report reference only; no coverage ID or plan-wide audit | PASS |

## Transaction Matrix

| Command group | Transaction owner | Evidence grouped | Result |
| --- | --- | --- | --- |
| UC01–03 | PlanningService | plan/items/request/history | PASS |
| UC04 | PlanApprovalService | action/request/state/history | PASS |
| UC05–07 | MaintenanceAssignmentService | coverage route/proposal/action/state/history | PASS |
| UC08 | ExecutionAcceptanceService | attempt/progress/item/plan/history | PASS |
| UC09–10 | ExecutionAcceptanceService | acceptance/signers/item/plan/history | PASS |
| UC11 draft/edit/final | MaintenanceReportService | report/plan/history | PASS |
| UC12 | EquipmentHistoryService read-only | no write | PASS |

Report finalization's late-history-failure test rolled back FINAL and REPORTED. Prior phase tests cover approval and acceptance rollback. No controller performs multi-row workflow orchestration.

## Optimistic Lock Matrix

| Command group | Expected version | Stale result |
| --- | --- | --- |
| Plan edit/submit/decision | Plan | 409 |
| Item route/vendor decisions | Item | 409 |
| First/subsequent execution start | Item + plan | 409 |
| Finish/repair/technical/handover | Item | 409 |
| Report create/edit/finalize | Plan | 409; no partial report/state |

Only the frozen plan/item entities carry `@Version`. Draft report writes immediately advance plan version. Plan/item row locks serialize concurrent attempts and final-state checks.

## Approval Round Matrix

| Subject | Preserved evidence | Final invariant |
| --- | --- | --- |
| PLAN_APPROVAL | New request per submission, one action per decided round | 0 DECIDED without action |
| VENDOR_SELECTION | Draft/submitted requests and prior revision rounds retained | 0 DECIDED without action |
| Pending duplicate | Service/partial unique guard | Rejected |
| Old decision overwrite/delete | No business API | Not exposed |

## Retention Audit

No HTTP DELETE/UPDATE endpoint was introduced for ApprovalAction, StatusHistory, MaintenanceExecution, MaintenanceProgressLog, AcceptanceRecord or FINAL MaintenanceReport. UC02 removal of audited items remains rejected. UC12 returns failed attempts and repair hand-off evidence in read-only form.

## API Contract Audit

| Contract | Result |
| --- | --- |
| Existing `/api` routes and names preserved | PASS |
| Explicit DTO/PageResponse boundary | PASS |
| Stable ErrorResponse 400/401/403/404/405/409/500 semantics | PASS |
| No entity/password/JWT/proxy/technicalSpec JSON leakage | PASS |
| Named commands; no generic status PATCH | PASS |
| Report create 201, edit/finalize/read 200; history read 200 | PASS |
| Duplicate report and stale versions 409 | PASS |
| Unknown equipment 404; foreign department 403 | PASS |

## Query/N+1 Audit

| Authenticated HTTP history sample | SQL statements | Finding |
| --- | ---: | --- |
| Simple seed equipment | 7 | Bounded |
| Multi-campaign equipment | 9 | Bounded |
| Multi-attempt/rework equipment | 9 | Bounded |

Queries include account reload. UC12 batches by item/execution/plan IDs rather than loading each child relation row by row. Earlier Phase 2 list query audits also pass. These counts do not prove the source's production-scale NFR latency thresholds.

## Database Regression

| Check | Final result |
| --- | ---: |
| Business tables | 14 |
| Business columns | 117 |
| Foreign keys | 31 |
| Successful Flyway migrations | 6, V001–V006 only |
| Canonical business rows after smoke cleanup | 603 |
| TEST/SMOKE plans | 0 |
| Plan/status-history mismatches | 0 |
| Item/status-history mismatches | 0 |
| DECIDED requests without action | 0 |
| COMPLETED items lacking latest-attempt technical and signed handover PASS | 0 |
| FINAL reports on non-REPORTED/CLOSED plans | 0 |

Clean `mvn test` and independent `mvn package`: **82 tests, 0 failures, 0 errors, 0 skips**. Final packaged-JAR health, four-role login and the full UC01–UC12 HTTP smoke passed. The frozen schema and source seed files were not changed.
