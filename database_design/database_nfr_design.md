# FINAL NFR-to-Database Design

## Security and scope

`user_account.role_code` represents the four V1 actor roles. `user_account.department_id` and `maintenance_plan_item.department_id_at_plan` support department filtering for UC10/12; `equipment.department_id` supports current inventory. Application queries and authorization enforce scope, including coverage and approval visibility. `password_hash` is a one-way digest, never plaintext (NFR-SEC-01–04). Providers are data, not users.

## Audit and retention

`approval_action` records director outcome; `status_history` records every plan/item transition with actor, old/new state, action, time and reason. Both are immutable through normal application flows. `maintenance_execution`/`maintenance_progress_log` and failed `acceptance_record` rows preserve fieldwork/rework evidence. No cryptographic snapshot or hash is required by NFR-AUDIT; removing them does not remove decision/transition history (NFR-AUDIT-01/02, NFR-REL-03).

## Concurrency and atomicity

`version` on plan and item detects lost updates in UC02/08. For UC04/07/10, transaction logic checks state and role, writes decision/acceptance, updates state and inserts StatusHistory atomically; any failure rolls back all changes (NFR-REL-01/02). DB unique constraints prevent duplicate terminal actions and duplicate assessment type on an attempt.

## Performance

NFR baselines are assumptions: 10,000 equipment, 100,000 log/history rows, 500 items/plan and 30–50 concurrent users. UC12 queries equipment → plan_item → execution/acceptance and plan report; UC11 aggregates items without stored duplicate counts. Start with PK/unique indexes plus essential history/approval indexes from `index_strategy.md`, then benchmark the stated response targets (NFR-PERF-01–03).

## Availability and extensibility

Proposed recovery goals (RPO ≤24 hours, RTO ≤2 hours) require tested database backups in implementation, not extra V1 tables (NFR-AVAIL). REPAIR_REQUIRED is a terminal maintenance result with retained equipment, logs and audit; V2 may later link it, but no repair table is frozen (NFR-MAINT-01). Form terminology remains visible through business columns even though scan upload is deferred (NFR-USE-01).

