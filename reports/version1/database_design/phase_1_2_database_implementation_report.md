# Phase 1.2 — Database Implementation Report

## 1. Objective

Implement the reviewed Phase 1.1 frozen V1 design as a reproducible PostgreSQL schema, with demo seed, executable positive/negative tests, clean rebuild, and audit evidence. This phase contains SQL and database tooling only; no backend or frontend code was created.

## 2. Frozen Design Input

The Phase 1.1 verdict is **PASS / FROZEN** for the documented university V1. Authoritative order: `database_design/phase_1_2_implementation_contract.md`, `data_dictionary.md`, `erd.md`, `database_constraints.md`, `design_decisions.md`, `database_traceability_matrix.md`. Business source: `docs/system_analysis_v1.pdf`; forms support the design. The dictionary has **14 tables and 117 columns**; the ERD inventory has **31 single-column FK relationships**. The two lifecycle vocabularies have 8 and 11 values. The superseded 20-table model was not implemented.

## 3. Local PostgreSQL Environment

| Item | Actual |
| --- | --- |
| PostgreSQL server/client | 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1) |
| Database | `medical_maintenance_db` |
| Host | Private Unix socket: `.local-postgres/run` |
| Port | 5432 on that socket; TCP disabled |
| Data directory | `.local-postgres/data` (Git-ignored, mode 700 parent) |
| Implementation | Six versioned PostgreSQL SQL migrations; no Flyway/Spring Boot yet |

The system `16/main` cluster accepts connections on port 5432, but this OS user has no PostgreSQL role there and `sudo -u postgres` requires a password. A private user-owned PostgreSQL 16 cluster was therefore initialized in the project. The scripts verify `data_directory` before database operations. No password is recorded in this report or Git.

## 4. Migration Strategy

| Order | Migration | Purpose |
| --- | --- | --- |
| 1 | `V001__master_data.sql` | `department`, `service_provider`, `user_account`, `equipment` |
| 2 | `V002__planning_and_coverage.sql` | `maintenance_coverage`, `maintenance_plan`, `maintenance_plan_item` |
| 3 | `V003__approval.sql` | `approval_request`, `approval_action` |
| 4 | `V004__execution_and_acceptance.sql` | `maintenance_execution`, `maintenance_progress_log`, `acceptance_record` |
| 5 | `V005__report_and_history.sql` | `maintenance_report`, `status_history` |
| 6 | `V006__required_indexes.sql` | CREATE NOW lookup indexes and conditional pending uniqueness |

This follows the frozen dependency order without an FK cycle. Each file uses `BEGIN`/`COMMIT`, explicit constraint names, and no GUI step or hidden session setting. The migration runner requires an empty `public` schema and stops on error. No metadata table was added, so the public business table count stays 14. Versioned SQL can be adopted by Flyway later without schema redesign.

## 5. Implemented Schema

| Group | Tables |
| --- | --- |
| Master Data | `department`, `user_account`, `equipment`, `service_provider` |
| Maintenance Workflow | `maintenance_coverage`, `maintenance_plan`, `maintenance_plan_item`, `maintenance_execution`, `maintenance_progress_log`, `acceptance_record` |
| Approval / Audit | `approval_request`, `approval_action`, `status_history` |
| Reporting | `maintenance_report` |

Exactly **14 tables, 117 columns and 31 FKs** exist. The removed `role`, `user_role`, `vendor_proposal`, `maintenance_assignment`, `acceptance_participant`, and `attachment` tables are absent.

## 6. PostgreSQL Type Mapping

| Dictionary concept | PostgreSQL type | Reason |
| --- | --- | --- |
| `identifier` | `BIGINT GENERATED ALWAYS AS IDENTITY` | Frozen generated surrogate PK strategy |
| `reference` | `BIGINT` | Matches referenced identity PK |
| `integer` | `INTEGER` | Version and attempt ordinal |
| `date` | `DATE` | Calendar periods and report date |
| `datetime` | `TIMESTAMPTZ` | Unambiguous event chronology |
| `boolean` | `BOOLEAN` | Active/deactivation flags |
| `string`, `text`, `enum` | `TEXT` | No arbitrary truncation limit; enum vocabularies use exact `CHECK` constraints |

No PostgreSQL enum type or state lookup table was introduced. `created_at` and applicable current statuses/versions have dictionary-aligned defaults.

## 7. Keys and Relationships

All 14 tables use generated `id` PKs. The 31 named FKs use `ON UPDATE RESTRICT ON DELETE RESTRICT`; there is no cascade delete of official records. Eight frozen ordinary UNIQUE constraints cover codes, usernames, plan equipment, terminal action, attempt ordinal, acceptance type and report per plan. Two partial unique indexes prevent duplicate PENDING approvals while retaining unlimited historical DECIDED rounds. Nullable approval/history targets use typed FKs and exclusive-target CHECKs. Referenced masters are deactivated using `active=false`; there is no universal `deleted_at`.

## 8. State Constraints

- Plan uses constrained `TEXT`: `DRAFT`, `SUBMITTED`, `REVISION_REQUIRED`, `APPROVED`, `IN_PROGRESS`, `AWAITING_REPORT`, `REPORTED`, `CLOSED`.
- Plan item uses a separate constrained `TEXT`: `PLANNED`, `UNDER_CONTRACT`, `PENDING_PROPOSAL`, `WAITING_VENDOR_APPROVAL`, `ASSIGNED_EXTERNAL`, `IN_MAINTENANCE`, `AWAITING_TECHNICAL_ACCEPTANCE`, `AWAITING_HANDOVER`, `COMPLETED`, `REWORK_REQUIRED`, `REPAIR_REQUIRED`.
- Tests updated fixtures through every allowed value and confirmed invalid values fail with SQLSTATE `23514`. Transition order belongs to Phase 2 domain services.

## 9. Approval Constraints

`approval_request` has a type-matched exclusive plan/item target, frozen request/status values, required submission/resolution timestamps, and submitted vendor evidence. `approval_action` has one terminal action per request, constrained outcome, and a comment for `REVISION_REQUIRED`. Partial unique indexes enforce one PENDING plan approval per plan and one PENDING vendor selection per item. Multiple DECIDED rounds for the same subject were accepted in tests. Actor role, request/action synchronization and atomic state/history changes remain application obligations.

## 10. Coverage / Provider Constraints

`maintenance_coverage.classification` distinguishes `UNKNOWN`, `FREE`, and `NOT_FREE`. FREE and NOT_FREE require verifier, time and nonblank basis; FREE also needs a provider. Unknown/absent coverage is not transformed into NOT_FREE. `maintenance_plan_item` stores provider, route and coverage reference directly, with local paired-field/coverage requirements. Same-equipment coverage, evidence validity, chosen provider and approval consistency require cross-row checks in Phase 2 services.

## 11. Execution / Rework Support

`maintenance_execution` stores one numbered attempt with its actual provider; `(plan_item_id,attempt_no)` is unique and `attempt_no > 0`. Two attempts for the same item passed tests. `maintenance_progress_log` stores multiple chronological notes per attempt; a blank work note is rejected. Earlier attempts and failed results can remain when an item moves through REWORK_REQUIRED. No Repair V2 table was created.

## 12. Acceptance Constraints

One `acceptance_record` table supports `TECHNICAL_ACCEPTANCE` and `HANDOVER_ACCEPTANCE`, with `PASS`/`FAIL`. `(execution_id,acceptance_type)` is unique. Signer IDs and times are paired; handover PASS needs department and VTYT signers. The test suite accepted technical PASS plus signed handover PASS and rejected invalid type/result, duplicate type, missing signers and incomplete pairs. Technical-before-handover and scope authority are cross-row/domain rules for Phase 2.

## 13. Audit Implementation

`status_history` has exactly one typed plan or item FK, actor, old/new state, action, reason and timestamp. Target-specific old/new state vocabularies are checked; reason is required for revision/rework/repair outcomes. It is append-oriented storage with no routine application edit/delete path in this phase. The freeze does not authorize a trigger for immutability, so Phase 2 must write one row per transition atomically and expose no mutation API. `approval_action` is separate decision evidence.

## 14. Concurrency Support

Only `maintenance_plan.version` and `maintenance_plan_item.version` exist. Both default to 0 and reject negative values. Java `@Version` and stale-write behavior are Phase 2 responsibilities.

## 15. Indexes

The 14 PK and 8 ordinary UNIQUE constraints create 22 supporting indexes. The following 8 explicit indexes bring the total to **30**; only frozen CREATE NOW/conditional uniqueness is present.

| Index | Table | Columns / predicate | Reason | UC/NFR |
| --- | --- | --- | --- | --- |
| `ix_item_equipment_plan` | `maintenance_plan_item` | `equipment_id, plan_id` | Device history | UC12 / PERF-01 |
| `ix_item_plan_status` | `maintenance_plan_item` | `plan_id, status` | Plan item counts | UC08/11 / PERF-02 |
| `ix_request_queue` | `approval_request` | `status, request_type, submitted_at` | Director queue | UC04/07 |
| `ux_request_pending_plan` | `approval_request` | `plan_id WHERE PENDING PLAN_APPROVAL` | One current plan round | UC03/04 |
| `ux_request_pending_item` | `approval_request` | `plan_item_id WHERE PENDING VENDOR_SELECTION` | One current vendor round | UC06/07 |
| `ix_history_plan_time` | `status_history` | `plan_id, action_timestamp` | Plan audit chronology | BR05 |
| `ix_history_item_time` | `status_history` | `plan_item_id, action_timestamp` | Item history | UC12 / BR05 |
| `ix_progress_execution_time` | `maintenance_progress_log` | `execution_id, event_at` | Work chronology | UC08/12 |

The execution/acceptance/report lookup indexes are supplied by UNIQUE constraints. Coverage date and equipment department/serial candidates remain deferred pending performance tests.

## 16. Seed / Demo Data

The deterministic, rerunnable `database/seeds/demo.sql` creates 2 departments, 4 users covering all V1 roles, 3 fictitious devices, 2 providers, 3 coverage rows (FREE, NOT_FREE, UNKNOWN), 1 plan and 2 items on the contract/external routes, 1 vendor approval request/action, and 7 status-history entries. It intentionally leaves execution, acceptance and reporting rows to the test fixture/application path. The four bcrypt digests were made from discarded random inputs, so no usable password appears in source. Running `seed.sh` a second time preserved all row counts.

## 17. Verification Tests

`database/tests/verify.sql` runs inside a transaction and rolls back fixtures. `database/tests/audit_schema.py` compares the complete table/column/type/nullability/FK sets with frozen files. The final run had **70 PASS assertions, 0 FAIL**; 48 assertions proved expected database rejection. Every SQL assertion from the final clean rebuild is listed below.

| Test | Expected | Actual | Status |
| --- | --- | --- | --- |
| demo prerequisite | Accept / metadata match | Accepted / matched | PASS |
| A: exactly 14 expected public tables | Accept / metadata match | Accepted / matched | PASS |
| A: removed tables absent | Accept / metadata match | Accepted / matched | PASS |
| A: exactly 117 frozen columns | Accept / metadata match | Accepted / matched | PASS |
| B: 14 primary keys and 31 foreign keys | Accept / metadata match | Accepted / matched | PASS |
| B: all FK delete/update actions are restrictive | Accept / metadata match | Accepted / matched | PASS |
| B: duplicate department code | Reject: UNIQUE `23505` | Rejected: `23505` | PASS |
| B: duplicate username | Reject: UNIQUE `23505` | Rejected: `23505` | PASS |
| B: invalid role code | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| B: department role requires department FK | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| B: duplicate equipment code | Reject: UNIQUE `23505` | Rejected: `23505` | PASS |
| B: invalid department FK | Reject: FK `23503` | Rejected: `23503` | PASS |
| B: valid department FK | Accept / metadata match | Accepted / matched | PASS |
| C: all eight frozen plan states accepted | Accept / metadata match | Accepted / matched | PASS |
| C: valid plan state and zero default version | Accept / metadata match | Accepted / matched | PASS |
| C: invalid plan state | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| C: reversed plan dates | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| J: negative plan version | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| D: all eleven frozen item states accepted | Accept / metadata match | Accepted / matched | PASS |
| D: valid item state and zero default version | Accept / metadata match | Accepted / matched | PASS |
| D: invalid item state | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| J: negative item version | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| H: duplicate equipment in one plan | Reject: UNIQUE `23505` | Rejected: `23505` | PASS |
| H: invalid assignment route | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| H: provider assignment requires route and coverage | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| E: UNKNOWN, FREE and NOT_FREE are distinct stored values | Accept / metadata match | Accepted / matched | PASS |
| E: invalid coverage classification | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| E: reversed coverage dates | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| E: FREE coverage needs a provider | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| E: verified classification needs evidence | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| F: valid plan approval target | Accept / metadata match | Accepted / matched | PASS |
| F: invalid approval request type | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| F: invalid approval request status | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| F: plan approval cannot be draft | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| F: both approval targets forbidden | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| F: no approval target forbidden | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| F: pending plan request is conditionally unique | Reject: UNIQUE `23505` | Rejected: `23505` | PASS |
| F: multiple historical decided rounds allowed | Accept / metadata match | Accepted / matched | PASS |
| F: submitted vendor request needs provider and rationale | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| F: vendor request may start as draft | Accept / metadata match | Accepted / matched | PASS |
| F: pending vendor request is conditionally unique | Reject: UNIQUE `23505` | Rejected: `23505` | PASS |
| G: invalid approval outcome | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| G: duplicate terminal approval action | Reject: UNIQUE `23505` | Rejected: `23505` | PASS |
| G: revision decision needs comment | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| I: typed plan and item histories accepted | Accept / metadata match | Accepted / matched | PASS |
| I: both history targets null | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| I: both history targets populated | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| I: item history rejects plan-only state | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| I: invalid old state for item | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| I: repair history needs reason | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| K: multiple execution attempts are supported | Accept / metadata match | Accepted / matched | PASS |
| K: duplicate attempt number | Reject: UNIQUE `23505` | Rejected: `23505` | PASS |
| K: execution end precedes start | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| K: nonpositive attempt number | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| K: progress log references execution | Accept / metadata match | Accepted / matched | PASS |
| K: blank progress note | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| K: invalid acceptance type | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| K: invalid acceptance result | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| K: one technical acceptance per attempt | Reject: UNIQUE `23505` | Rejected: `23505` | PASS |
| K: handover PASS requires two signers | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| K: signer ID/time must be paired | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| K: valid technical and signed handover accepted | Accept / metadata match | Accepted / matched | PASS |
| L: draft report accepted | Accept / metadata match | Accepted / matched | PASS |
| L: invalid report status | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| L: one report per plan | Reject: UNIQUE `23505` | Rejected: `23505` | PASS |
| L: final report requires work_done and finalized_at | Reject: CHECK `23514` | Rejected: `23514` | PASS |
| M: referenced department cannot be deleted | Reject: FK `23503` | Rejected: `23503` | PASS |
| M: referenced plan cannot be deleted | Reject: FK `23503` | Rejected: `23503` | PASS |
| indexes: required eight named indexes exist | Accept / metadata match | Accepted / matched | PASS |
| indexes: deferred candidates remain absent | Accept / metadata match | Accepted / matched | PASS |

## 18. Negative Tests

Of the 48 invalid cases, **35** failed with `23514` (CHECK), **10** with `23505` (UNIQUE), and **3** with `23503` (FK). Representative cases include invalid plan/item states, unknown coverage value, conflicting approval/history targets, duplicate pending approval/action, duplicate equipment within a plan, unsigned handover PASS, duplicate acceptance/report, and deletion of referenced records. Any unexpected success or wrong SQLSTATE stops the runner.

## 19. Clean Rebuild Evidence

Executed from the repository root after `source scripts/use-toolchain.sh`:

```bash
./database/scripts/reset_database.sh --yes
```

Observed sequence: dedicated dev database dropped → recreated on private socket → migrations V001–V006 applied in order → demo seed applied → SQL tests PASS → exact dictionary/ERD audit PASS. Terminal summary:

```text
SCHEMA AUDIT PASS: 14 tables, 117 columns, 31 FKs, 30 indexes, 99 constraints
TEST SUMMARY PASS: 70 assertions, 48 expected rejections, 0 failures.
CLEAN REBUILD PASS.
```

No manual table creation was used. The final database remains available with only demo seed data; verification fixtures were rolled back.

## 20. Frozen Design Comparison

| Metric | Frozen | Implemented | Result |
| --- | ---: | ---: | --- |
| Business tables | 14 | 14 | PASS |
| Dictionary columns | 117 | 117 | PASS |
| Foreign keys | 31 | 31 | PASS |
| Plan states | 8 | 8 | PASS |
| Plan item states | 11 | 11 | PASS |

Implemented extras within contract: 14 PK constraints, 8 ordinary UNIQUE constraints, 46 CHECK constraints, 2 partial unique indexes and 6 lookup indexes. Total: **99 PostgreSQL constraints and 30 indexes**.

## 21. Implementation Deviations

**None from the frozen schema.** The private user-owned local cluster is an environment adaptation because access to the system cluster requires credentials/sudo. It uses the same PostgreSQL major version and port 5432 on a private Unix socket; migrations remain portable SQL. This does not alter tables, columns, FKs, states or cardinalities.

## 22. Audit Findings

- Frozen source files agreed on 14 tables, 31 FKs, 117 columns and 8/11 state values.
- `psql --version`, `pg_isready`, and `pg_lsclusters` confirmed PostgreSQL 16.15 and system cluster availability, but the OS account had no system-cluster role.
- Anaconda's `pg_config` reported a bindir without `pg_ctl`; the scripts now use the verified PostgreSQL 16 system binary directory.
- SQL test introspection initially compared `information_schema.sql_identifier[]` with `text[]`; an explicit cast corrected the test, without any schema change.
- The final schema audit found no unexpected table, column, nullability, conceptual type mapping or FK.

## 23. Fixes Applied

Used an isolated cluster to avoid system privilege changes. Set `PG_BIN` to `/usr/lib/postgresql/16/bin`, cast catalog identifiers to `TEXT` in the SQL test, expanded state/negative coverage, and added exact schema audit plus a test summary. A clean rebuild after these fixes passed.

## 24. Final Status

**PASS** — all Phase 1.2 database implementation gates passed for the frozen university V1.

## 25. Evidence

- `reports/phase_1_2_database_schema_audit.json`: 14 tables, 117 columns, 31 FKs, 14 PKs, 8 UNIQUE, 46 CHECK, 30 indexes, 99 total constraints, empty error list.
- Final terminal result: `TEST SUMMARY PASS: 70 assertions, 48 expected rejections, 0 failures`; `CLEAN REBUILD PASS`.
- `docs/system_analysis_v1.pdf` SHA-256 remained `8170720b365d683917b045b273d192d61e103995f281f61bd8ab9fb3f998736b`; `docs/temple.pdf` remained `4e012312cfe460320edbed8debb62874d810e2e2e9be813dc73dcc5ad51495a2`.
- The `database/implementation_traceability.md` table maps all 14 final tables and frozen constraint groups to migrations/tests.

## 26. Remaining Issues

No Phase 1.2 schema blocker. The project-owned cluster is Unix-socket-only; Phase 2 must configure an authenticated database endpoint appropriate to its Spring Boot runtime. Domain-service enforcement of transition order, role/scope authorization, evidence consistency and atomic history writes is intentionally pending Phase 2. Hospital-specific digital policy questions remain outside this frozen university V1.

## 27. Phase 2 Handoff

Backend Foundation can use the exact 14-table schema and versioned SQL files, PostgreSQL identity keys, constrained string states, 31 restrictive FKs, partial pending approval uniqueness, optimistic version columns, and the DB test/reset commands. Before application integration, provision the chosen PostgreSQL connection securely and implement the application-enforced rules listed in `database_constraints.md`. Do not change the frozen schema without review.

## 28. Slide-ready Summary

- Phase 1.1 frozen schema implemented exactly: 14 tables, 117 columns, 31 FKs.
- Six ordered PostgreSQL migrations rebuild the database from zero.
- 8 plan and 11 item states are enforced with `TEXT` + `CHECK`.
- Approval, coverage, work attempts, acceptance, reporting and typed audit storage are present.
- 70 assertions passed, including 48 expected invalid-data rejections.
- Clean reset, migrate, seed and exact schema audit all passed.
- Phase 2 can integrate the migrations and implement cross-row workflow rules.
