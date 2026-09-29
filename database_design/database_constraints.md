# FINAL FROZEN DATABASE CONSTRAINTS

## DB-enforced structure

- PK: generated `id` on all 14 tables. FKs: the exact 31 typed relationships in `erd.md`. Prevent routine parent deletion when official children exist.
- Required `NOT NULL` fields are marked N in `data_dictionary.md`. Cross-row workflow conditions are deliberately not represented as row-level CHECKs.
- UNIQUE: `department.code`, `user_account.username`, `equipment.equipment_code` (project policy), `maintenance_plan_item(plan_id,equipment_id)`, `approval_action.request_id`, `maintenance_execution(plan_item_id,attempt_no)`, `acceptance_record(execution_id,acceptance_type)` and `maintenance_report.plan_id`.
- Conditional unique pending requests: at most one PENDING PLAN_APPROVAL per plan, and one PENDING VENDOR_SELECTION per plan item. Historical DECIDED requests are unlimited. For DRAFT vendor requests, one active draft/item is a UI/service policy.
- CHECK: exact role, plan, item, coverage, route, request, outcome, acceptance and report values from the dictionary. Plan period end ≥ start; coverage end ≥ start when both supplied; execution end ≥ start; version ≥ 0; attempt_no > 0.
- CHECK: ApprovalRequest has exactly one of plan_id/plan_item_id matching request_type; PLAN_APPROVAL cannot be DRAFT; submitted_at is present for PENDING/DECIDED and resolved_at for DECIDED. VENDOR_SELECTION PENDING/DECIDED needs proposed_provider_id and nonblank rationale.
- CHECK: StatusHistory has exactly one of plan_id/plan_item_id; new_state nonblank, old_state nullable only for creation. Acceptance signer ID/time pairs are jointly present or absent. FINAL report has finalized_at and work_done.
- Equipment serial and provider name are **not** unique: no source supports that assumption. Report number format is unconfirmed.

## Application-enforced workflow

| BR | Database support | Backend/domain service obligation |
| --- | --- | --- |
| BR01 | Plan status, item FKs, version, approval history | Permit editing only DRAFT/REVISION_REQUIRED; revision save returns plan to DRAFT; reject edits in submitted/later states. |
| BR02 | Plan/item state and provider/coverage/request FKs | Before work, check approved/in-progress plan and a valid provider route. A pre-approval UC05 assessment may be recorded but cannot start work. |
| BR03 | Coverage UNKNOWN/FREE/NOT_FREE, item route/provider/coverage, vendor request/action | Missing/UNKNOWN blocks decision. Verified FREE chooses coverage provider; verified NOT_FREE requires approved vendor request for same item/provider before external work. |
| BR04 | Execution, typed acceptance, explicit signer FKs/times | Technical PASS on current attempt precedes handover PASS; department and VTYT confirm; only then item COMPLETED. Failed attempts remain. |
| BR05 | Typed StatusHistory FKs and required audit fields | Append one immutable history row for every plan/item state transition in the same transaction; no routine edit/delete endpoint. |

Authorization is service/query enforced: director only UC04/07; department users see and sign only their scope. The database supports filtering through equipment.department_id and item.department_id_at_plan. A FK does not itself authorize access.

## Cross-row integrity

The service must verify item.coverage_id belongs to item.equipment_id and is currently applicable/verified. Item.assigned_provider_id must match the FREE coverage provider or the approved vendor request provider. Execution.provider_id is copied from the validated current choice at start and never edited after an attempt begins. Hand-over PASS is valid only for a current execution with technical PASS. State update, decision/acceptance record and StatusHistory insert are atomic.

## Retention

Draft-only unreferenced records may be physically removed. Submitted plans/items, requests/actions, coverage evidence used for routing, executions, progress logs, acceptances, final reports and histories remain. Master accounts/equipment/departments/providers use active=false rather than deletion when referenced. No universal soft-delete marker is added. Retention duration remains a non-blocking hospital policy question.

