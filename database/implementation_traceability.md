# Phase 1.2 Implementation Traceability

**Authority:** `database_design/phase_1_2_implementation_contract.md`, `data_dictionary.md`, `erd.md`, `database_constraints.md`, and `index_strategy.md`. All rows below were verified after a clean rebuild. The executable dictionary/ERD comparison is `database/tests/audit_schema.py`; its result is `reports/phase_1_2_database_schema_audit.json`.

## Frozen tables → migrations → verification

| Frozen Table | Migration | Verification Test | Status |
| --- | --- | --- | --- |
| department | V001 master data | Exact columns/FKs audit; code UNIQUE; referenced delete | PASS |
| user_account | V001 master data | Exact columns/FKs audit; role, scope, username UNIQUE | PASS |
| equipment | V001 master data | Exact columns/FKs audit; FK valid/invalid; code UNIQUE | PASS |
| service_provider | V001 master data | Exact columns/FKs audit; seed/reference | PASS |
| maintenance_coverage | V002 planning and coverage | Exact columns/FKs audit; UNKNOWN/FREE/NOT_FREE, verifier, dates | PASS |
| maintenance_plan | V002 planning and coverage | Exact columns/FKs audit; all 8 states, invalid state, version, dates | PASS |
| maintenance_plan_item | V002 planning and coverage | Exact columns/FKs audit; all 11 states, item uniqueness, route, version | PASS |
| approval_request | V003 approval + V006 partial indexes | Exact columns/FKs audit; target, status, pending uniqueness, historical rounds | PASS |
| approval_action | V003 approval | Exact columns/FKs audit; one terminal action, outcome, revision comment | PASS |
| maintenance_execution | V004 execution and acceptance | Exact columns/FKs audit; repeated attempts, positive ordinal, dates | PASS |
| maintenance_progress_log | V004 execution and acceptance | Exact columns/FKs audit; execution FK, nonblank note | PASS |
| acceptance_record | V004 execution and acceptance | Exact columns/FKs audit; type/result, per-attempt uniqueness, signer pairs | PASS |
| maintenance_report | V005 report and history | Exact columns/FKs audit; one per plan, FINAL fields | PASS |
| status_history | V005 report and history | Exact columns/FKs audit; exclusive typed target and target state vocabulary | PASS |

## Frozen constraints → migration location → test

| Frozen Constraint | Migration Location | Test | Status |
| --- | --- | --- | --- |
| Generated `id` PK on 14 tables | V001–V005 (`pk_*`) | Catalog count: 14 PK; exact column audit | PASS |
| All 31 typed FKs, restrictive updates/deletes | V001–V005 (`fk_*`) | ERD FK set audit; invalid/valid FK; delete rejection; action catalog check | PASS |
| `department.code` UNIQUE | V001 | Duplicate code rejected | PASS |
| `user_account.username` UNIQUE | V001 | Duplicate username rejected | PASS |
| `equipment.equipment_code` UNIQUE; serial not unique | V001 | Duplicate code rejected; catalog audit | PASS |
| `(plan_id,equipment_id)` UNIQUE | V002 `uq_plan_item_equipment` | Duplicate item rejected | PASS |
| `approval_action.request_id` UNIQUE | V003 `uq_approval_action_request` | Duplicate action rejected | PASS |
| `(plan_item_id,attempt_no)` UNIQUE | V004 `uq_execution_item_attempt` | Duplicate attempt rejected | PASS |
| `(execution_id,acceptance_type)` UNIQUE | V004 `uq_acceptance_execution_type` | Duplicate acceptance rejected | PASS |
| `maintenance_report.plan_id` UNIQUE | V005 `uq_report_plan` | Duplicate report rejected | PASS |
| One PENDING plan request / plan | V006 `ux_request_pending_plan` | Duplicate pending plan request rejected | PASS |
| One PENDING vendor request / item | V006 `ux_request_pending_item` | Index audited; same predicate as contract | PASS |
| 8 plan and 11 item status values | V002 `ck_plan_status`, `ck_item_status` | All valid states accepted; invalid states rejected | PASS |
| Role, coverage, route, request, outcome, acceptance and report vocabularies | V001–V005 `ck_*` | Representative invalid values rejected; coverage values stored | PASS |
| Plan, coverage and execution date order | V002, V004 `ck_*_dates` / `ck_plan_period` | Reversed dates rejected | PASS |
| Plan/item version >= 0; attempt_no > 0 | V002, V004 | Defaults and invalid numbers tested | PASS |
| Exclusive request subject; submitted vendor evidence | V003 `ck_request_*` | Both/none target and missing proposal rejected | PASS |
| Exclusive history target and target-specific old/new state | V005 `ck_history_*` | Both/none target and mismatched state rejected | PASS |
| Signer ID/time pairing; handover PASS signers | V004 `ck_acceptance_*` | Missing signers/time rejected | PASS |
| FINAL report timestamp/work narrative | V005 `ck_report_final_fields` | Invalid FINAL rejected | PASS |
| Six CREATE NOW lookup indexes | V006 `ix_*` | Named-index audit | PASS |
| Deferred candidate indexes absent | V006 (not created) | Absence test | PASS |

## Service obligations carried forward

The database deliberately does not use fragile cross-table CHECKs or triggers for plan approval before execution, coverage/provider matching, scoped signer authority, transition order, one history row per transition, and atomic decision/acceptance/history writes. These are explicitly assigned to Phase 2 domain services by the frozen contract. The schema contains the necessary FKs, states, version fields and audit rows.
