# PHASE 1.2 IMPLEMENTATION CONTRACT — FROZEN V1

This contract and the final files named in `README.md` are the only schema authority for Phase 1.2. It authorizes **no work now**; implementation begins after human review. Source business authority remains `docs/system_analysis_v1.pdf`.

## 1. Final Tables

Exactly 14: `department`, `user_account`, `equipment`, `service_provider`, `maintenance_coverage`, `maintenance_plan`, `maintenance_plan_item`, `approval_request`, `approval_action`, `maintenance_execution`, `maintenance_progress_log`, `acceptance_record`, `maintenance_report`, `status_history`.

## 2. Migration Dependency Order

Logical dependency order only, not SQL: (1) department, service_provider; (2) user_account, equipment; (3) maintenance_coverage and maintenance_plan; (4) maintenance_plan_item; (5) approval_request; (6) approval_action; (7) maintenance_execution; (8) maintenance_progress_log and acceptance_record; (9) maintenance_report and status_history. Constraints/indexes may be staged within migrations while preserving the final relationships. There is no FK cycle.

## 3. Final Columns

Implement exactly the 117 rows in `data_dictionary.md`. The dictionary's names, conceptual types, nullability, source class and validation text are authoritative. No old Phase 1.1 snapshot, attachment, role-junction, proposal, assignment or participant fields remain.

## 4. Required PK / FK / UNIQUE / CHECK

- Generated PK `id` on all final tables. Implement the 31 FKs in `erd.md` with restrictive deletion behavior for referenced official records.
- UNIQUE: department.code, user_account.username, equipment.equipment_code, (maintenance_plan_item.plan_id, equipment_id), approval_action.request_id, (maintenance_execution.plan_item_id, attempt_no), (acceptance_record.execution_id, acceptance_type), maintenance_report.plan_id.
- Conditionally unique PENDING plan approval per plan and PENDING vendor approval per plan item.
- CHECK domain values exactly as listed in dictionary. CHECK period/coverage/execution date order, nonnegative versions, positive attempt number, exclusive approval target, exclusive history target, matched signer ID/time pairs and final-report completion fields.
- Application validates cross-table evidence consistency, nonblank submitted vendor rationale, actor roles and transition order. See `database_constraints.md` for BR01–BR05 enforcement.

## 5. Status Storage

Use string columns with database CHECK vocabularies and matching Java enums in Phase 1.2. Plan has exactly eight values; item exactly eleven. No PostgreSQL enum type or lookup-state table. Current status is on plan/item; StatusHistory is append-only evidence. Enforce the transition lists in `logical_data_model.md` in domain services. REPAIR_REQUIRED is terminal V1 and reportable but never counted as COMPLETED.

## 6. Version / Optimistic Locking

Only `maintenance_plan.version` and `maintenance_plan_item.version`. Start at zero and increment on successful edits/transitions. A stale write fails visibly; no last-write-wins.

## 7. Audit Rules

Every plan/item transition inserts exactly one StatusHistory with target, actor, old/new state, action, time and relevant reason. Initial creation may have null old_state. ApprovalAction is a separate append-only director decision. For UC04, UC07, UC10 and all transitions, state/decision/acceptance/history changes are one database transaction. Do not expose business UPDATE/DELETE on ApprovalAction or StatusHistory.

## 8. Delete / Retention Rules

Only unsubmitted, unreferenced drafts can be hard-deleted through ordinary workflow. Once submitted or referenced by official evidence, retain. Deactivate referenced accounts, departments, equipment and providers using active=false. Preserve coverage decisions used for routing, all approval rounds, work attempts, failed assessments, final reports and history. No blanket deleted_at column.

## 9. Required Indexes for Initial Implementation

**CREATE NOW:** PK/UNIQUE-backed indexes plus maintenance_plan_item(equipment_id, plan_id), maintenance_plan_item(plan_id, status), approval_request(status, request_type, submitted_at), status_history(plan_id, action_timestamp), status_history(plan_item_id, action_timestamp), maintenance_progress_log(execution_id, event_at). The execution and acceptance lookup indexes are supplied by their UNIQUE constraints.

**DEFER UNTIL PERFORMANCE TEST:** maintenance_coverage(equipment_id, effective_from, effective_to), equipment(department_id, serial_number), and any further report aggregate index. Benchmark against NFR-PERF baselines before adding.

## 10. Demo / Seed Data Scope

If Phase 1.2 later needs demonstration data, seed only a few departments, one account per V1 role, sample equipment/providers, one verified FREE and one verified NOT_FREE coverage record, and a small plan with one item on each route. Use non-secret demo password hashes generated during implementation; do not place credentials in source. Workflow records should preferably be produced through the application path to exercise rules. **No seed data is generated in this freeze.**

## 11. Prohibited Implementation Deviations

Phase 1.2 must not add/remove/rename tables, add business columns, alter cardinalities, change the exact state values, add Repair V2 tables, reintroduce generic attachments/RBAC/history abstractions, or infer NOT_FREE from missing coverage without stopping and reporting a design conflict. Resolve a real source contradiction through a reviewed design amendment before migration changes.

