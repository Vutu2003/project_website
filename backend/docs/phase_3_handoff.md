# Phase 3 Handoff

Phase 2 freezes the backend **foundation**, not maintenance workflow behavior. Extend it through business services and reviewed API DTOs. Read the [Phase 1.2 implementation contract](../../database_design/phase_1_2_implementation_contract.md), [dictionary](../../database_design/data_dictionary.md), [constraints](../../database_design/database_constraints.md) and [architecture summary](backend-architecture.md) before writing commands.

## 1. Frozen Backend Foundation

- Java 17, Spring Boot 3.5.16, PostgreSQL 16, Flyway V001–V006, JPA/Hibernate schema validation.
- Fourteen mapped entities and repositories; all relationships lazy and no destructive cascade.
- Eight read-only GET endpoints, explicit DTO/mapper boundary, `PageResponse` pagination and `ErrorResponse` errors.
- Spring Security, BCrypt, one-hour JWT Bearer authentication, stateless sessions, database account reload per request, director-only pending queue.
- `CurrentUser` supplies a trusted authenticated actor context. The frontend is not implemented; UC01–UC12 services are summarized in Sections 10–13 and frozen in [backend-business-freeze.md](backend-business-freeze.md).

## 2. Database Contract

The Phase 1.1/1.2 design is **14 tables, 117 columns, 31 FKs**. Flyway owns schema; Hibernate uses `ddl-auto=validate`. Plan current states are exactly:

`DRAFT`, `SUBMITTED`, `REVISION_REQUIRED`, `APPROVED`, `IN_PROGRESS`, `AWAITING_REPORT`, `REPORTED`, `CLOSED`.

Plan item current states are exactly:

`PLANNED`, `UNDER_CONTRACT`, `PENDING_PROPOSAL`, `WAITING_VENDOR_APPROVAL`, `ASSIGNED_EXTERNAL`, `IN_MAINTENANCE`, `AWAITING_TECHNICAL_ACCEPTANCE`, `AWAITING_HANDOVER`, `COMPLETED`, `REWORK_REQUIRED`, `REPAIR_REQUIRED`.

Do not treat `REPAIR_REQUIRED` as a Phase 2 repair workflow. `maintenance_coverage.classification=UNKNOWN` or missing evidence must not be silently treated as `NOT_FREE`.

## 3. Existing Security Context

`CurrentUser.get()` returns `AuthenticatedUser(id, username, role, departmentId)` from `SecurityContext`. The JWT filter verifies the token, reloads `user_account`, rejects inactive users and uses the current database role. `KHOA_PHONG` has a department ID in the seed. **Full department-scoped access is not enforced yet**; Phase 3 must apply scope to relevant queries and commands. Do not parse JWT in business services or authorize by the unverified request body.

## 4. Existing Error Contract

`ErrorResponse` contains `timestamp`, `status`, `error`, `code`, `message`, `path`, `fieldErrors`. Current semantics: 400 invalid input, 401 authentication failure, 403 forbidden role, 404 missing resource, 405 unsupported method, 409 generic conflict, 500 safe unexpected failure. Phase 3 should translate stale optimistic updates and rule conflicts into consistent 409/4xx responses without exposing SQL, stack traces or entities.

## 5. Existing Repository Capabilities

All 14 tables have `JpaRepository<Entity, Long>`. Focused reads cover equipment/department pages, plans/items, coverage evidence by date, pending and historical approvals, numbered executions, progress, typed acceptance, report and status history. `DEMO-EQ-004` exercises UC12 across two campaigns. Fetch plans keep current list queries bounded; check new DTO reads for N+1. Repositories supply persisted evidence, not approval or transition decisions. See [repository-query-guide.md](repository-query-guide.md).

## 6. Rules Phase 3 Must Enforce

- **BR01:** edits are allowed only while a plan is `DRAFT` or `REVISION_REQUIRED` under the frozen resolution; a revision save returns to `DRAFT` before resubmission.
- **BR02:** maintenance work starts only after approved/in-progress plan state and validated coverage/provider route. A pre-approval coverage assessment can exist without starting work.
- **BR03:** verified `FREE` coverage uses its covered provider. Otherwise external provider assignment requires the proper director approval; `UNKNOWN`/missing evidence blocks automatic routing.
- **BR04:** handover PASS follows technical PASS on the current execution attempt with scoped department and VTYT signers. Failed technical/handover assessment remains; rework creates a new attempt. Damage may hand off to `REPAIR_REQUIRED` without a Repair V2 table.
- **BR05:** each plan/item transition appends one `status_history` row with actor, old/new state, action/time and needed reason. Preserve approval rounds, director actions, failed attempts and official evidence.

Validate role and department scope where the business action occurs. The current route-level pending queue restriction does not authorize an approval decision.

## 7. Things Phase 3 Must Not Do

Do not casually redesign the frozen schema, migration history, role or state vocabularies, DTO/error conventions or JWT principal. Do not put multi-row command orchestration directly in controllers, return JPA entities, bypass `CurrentUser`, delete official audit history, conflate `UNKNOWN` with `NOT_FREE`, or expose inherited repository `delete` methods as ordinary workflow. A genuine design conflict requires review before a migration change.

## 8. Business Service Transaction Boundary

Each command service owns one `@Transactional` boundary and validates preconditions before committing related rows. UC03, UC04, UC08–UC11 now follow these patterns:

- **Submit plan:** validate editable state/items → update plan state → create `approval_request` → append `status_history`.
- **Director decision:** verify director and pending request → create immutable `approval_action` → resolve request → change target state → append `status_history`.
- **Maintenance transition:** validate route/current attempt → write execution or assessment evidence → update item state → append `status_history`.

All pieces succeed or roll back together. Only `MaintenancePlan` and `MaintenancePlanItem` have `@Version`; plan and item commands translate stale writes into a consistent conflict response. Preserve approval/acceptance/history retention rules and avoid custom bulk updates/deletes that bypass them.

## 9. Phase 3 Continuation

UC11–UC12 now complete the V1 backend workflow; use the business freeze and reporting/history guide for frontend integration. Reuse the 603-row synthetic fixture and its scenario catalog for read evidence, and create isolated test fixtures for writes. Keep Flyway as the only schema owner. Review [security-guide.md](security-guide.md) before exposing new routes; add exact CORS origins only when frontend integration requires them. The Phase 2.6 [freeze report](../../reports/backend/phase_2_6_backend_foundation_freeze_report.md) records the baseline to preserve.

## 10. Phase 3.1 Implemented Baseline (2026-09-26)

UC01–UC04 now use `PlanningService` and `PlanApprovalService` with transactional commands, role checks, version preconditions and matching history. The four command routes and additive UC02 semantics are documented in [planning-approval-workflow.md](planning-approval-workflow.md). The audited item-removal conflict is deliberate under the frozen schema. Phase 3.2 should extend the existing service/API conventions for UC05–UC07 without changing these approval rounds or Phase 2 DTO/error/security contracts. See the [Phase 3.1 report](../../reports/backend/phase_3_1_planning_approval_report.md) and [business audit](../../reports/backend/phase_3_1_business_audit.md).

## 11. Phase 3.2 Implemented Baseline (2026-09-26)

UC05–UC07 now route verified FREE coverage to its contracted provider, send verified NOT_FREE items through DRAFT/PENDING vendor requests, and resolve director approval/revision in preserved rounds. UNKNOWN or missing coverage blocks routing. Item version, role, plan approval, provider identity, date applicability and BR05 history checks are service-level and transactional. See [provider-routing-workflow.md](provider-routing-workflow.md), the [Phase 3.2 report](../../reports/backend/phase_3_2_provider_routing_report.md) and [business audit](../../reports/backend/phase_3_2_business_audit.md). Phase 3.3 must revalidate route evidence when UC08 starts execution; no execution command exists yet.

## 12. Phase 3.3 Implemented Baseline (2026-09-26)

UC08–UC10 now create numbered execution attempts, append progress, close work, record typed technical and handover assessments, preserve rework attempts, and hand off damage to the terminal V1 `REPAIR_REQUIRED` state. First work starts the plan; all final item outcomes make it `AWAITING_REPORT`. Handover PASS requires a Khoa/Phòng actor scoped to the item's historical department and a separately authenticated VTYT confirmer in the same command. See [execution-acceptance-workflow.md](execution-acceptance-workflow.md) and the [Phase 3.3 report](../../reports/backend/phase_3_3_execution_acceptance_report.md). Phase 3.4 implements UC11–UC12 on this evidence model.

## 13. Phase 3.4 Implemented Baseline (2026-09-26)

UC11 provides one DRAFT/FINAL report per fully resolved plan, separate COMPLETED/REPAIR_REQUIRED counts, plan-version checks and atomic `AWAITING_REPORT → REPORTED`. UC12 provides department-scoped equipment history across plans and attempts with batch queries, failed assessments and concise report references. KHOA_PHONG equipment and plan GETs are scoped to current/historical custody. The final [business freeze](backend-business-freeze.md) and [reporting/history guide](reporting-history-workflow.md) supersede older phase-time handoff wording for frontend work.
