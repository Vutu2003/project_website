# Phase 2.3 — Repository & Query Foundation

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-25  
**Status:** **PASS**

## 1. Objective

Provide a thin Spring Data repository for each frozen entity and verify the read patterns needed by later services and APIs. This phase covers lookup, pagination, deterministic order, deliberate fetch plans and the UC12 equipment-history retrieval path. It adds no workflow behavior.

## 2. Starting Point

Phase 2.1 already connected Spring Boot to PostgreSQL and Flyway. Phase 2.2 mapped all 14 tables, 117 columns and 31 FKs into validated JPA entities with lazy relationships. The backend dev database contained the Phase 1.3 603-row synthetic seed; no repository interface existed.

## 3. What is a Repository?

A repository is an interface through which later services request stored data. It can expose lookups such as “find equipment by code” without embedding SQL in a controller. Repositories in this phase return entities and evidence; they do not approve plans or decide whether maintenance may start.

## 4. What is Spring Data JPA?

Spring Data JPA builds implementations for interfaces extending `JpaRepository`. It can derive simple queries from method names, run JPQL for a specific query shape, and package list results into pages. Hibernate then translates JPA operations to SQL against the already frozen PostgreSQL schema.

## 5. Persistence Flow

```text
Future Controller
      ↓
Future Service
      ↓
Repository       ← Phase 2.3
      ↓
JPA / Hibernate
      ↓
PostgreSQL
```

## 6. Repository Inventory

| Repository | Entity | Main future use |
| --- | --- | --- |
| DepartmentRepository | Department | Unit code lookup |
| UserAccountRepository | UserAccount | Account, role, department lookup |
| EquipmentRepository | Equipment | Paged equipment and device code lookup |
| ServiceProviderRepository | ServiceProvider | Active provider reference list |
| MaintenanceCoverageRepository | MaintenanceCoverage | Dated coverage evidence |
| MaintenancePlanRepository | MaintenancePlan | Paged/status plans and detail |
| MaintenancePlanItemRepository | MaintenancePlanItem | Plan detail, status and device history |
| ApprovalRequestRepository | ApprovalRequest | Paged director queue and rounds |
| ApprovalActionRepository | ApprovalAction | Decision by request |
| MaintenanceExecutionRepository | MaintenanceExecution | Numbered attempts |
| MaintenanceProgressLogRepository | MaintenanceProgressLog | Chronological work notes |
| AcceptanceRecordRepository | AcceptanceRecord | Technical/handover assessments |
| MaintenanceReportRepository | MaintenanceReport | Plan report lookup |
| StatusHistoryRepository | StatusHistory | Plan/item transition chronology |

Spring startup found **14 JPA repository interfaces**. Each extends `JpaRepository<Entity, Long>`; there is no custom implementation class.

## 7. Query Design Principles

Methods describe stored data, not commands. Derived methods handle straightforward filters; two JPQL methods handle date-aware coverage ordering, and one JPQL fetch join handles plan detail. To-one `@EntityGraph` fetch plans serve list displays. There are no native SQL, bulk updates, deletes, or business-specific write methods.

## 8. Derived Queries

A **derived query** is generated from a method name. `findByEquipmentCode` filters the device code; `findByPlan_IdAndStatus` filters an item's parent plan and state; `findByExecution_IdOrderByEventAtAscIdAsc` orders notes by time and ID. The final `Id` tie breaker makes chronology stable when timestamps match. `ServiceProvider` has no frozen `code` column, so the suggested `findByCode` example was not added there.

## 9. JPQL / EntityGraph

**JPQL** queries Java entity names and fields, so it stays aligned with the validated mapping. Coverage JPQL returns all evidence or rows whose date range contains a supplied date, putting undated starts last; the caller must still assess classification and route. `MaintenancePlanRepository.findWithCreatorById` uses `JOIN FETCH` to load a plan and its creator in one query. `@EntityGraph` declares related to-one fields to load for equipment, plan-item and approval lists. A **native query** would use database SQL directly; none was needed.

## 10. Pagination and Sorting

`Pageable` carries page number, page size and `Sort`; `Page` also reports the total number of matches. Equipment, plans, plan items, approval queues, reports and actor history have pageable methods. Tests verified page sizes, total counts, distinct first/second pages and deterministic ordering. Callers include `id` as a tie breaker, for example `equipmentCode ASC, id ASC` or `submittedAt ASC, id ASC`.

## 11. Equipment Queries

The repository supports code lookup, department filter, active filter and a paged list. Equipment and department are fetched together for display. The `HOI_SUC` seed department returned 8 devices; a page size of 3 returned three rows on each of the first two distinct pages. The full equipment list reported 40 rows.

## 12. Plan / PlanItem Queries

Plans can be paged, filtered by status, and loaded with their creator; items can be filtered by plan, plan plus state, equipment, item state and historical department. Plan items for `DEMO-EQ-004` returned two different campaigns and both COMPLETED and REPAIR_REQUIRED outcomes. Plan-item paging by equipment uses the parent plan's period start plus IDs for deterministic history order.

## 13. Approval Queries

Pending plan and vendor requests use a pageable `(status, requestType)` filter; a general pending queue is available too. The seed returned 1 pending plan request and 2 pending vendor requests. Requests by plan or item retain chronological rounds; the October approved plan retained two requests and two associated director actions. No repository method makes a decision or changes request status.

## 14. Execution / Progress Queries

Executions are read by plan item in `attemptNo` order; a latest-attempt and item-plus-number lookup are available. `DEMO-EQ-004` retained attempts 1 and 2 from technical rework. Progress logs are ordered by `eventAt, id`, preserving work and damage evidence without rewriting earlier attempts.

## 15. Acceptance / Report Queries

Acceptance rows are read in `observedAt, id` order or by execution plus acceptance type. Tests found technical FAIL on the first rework attempt and PASS/HANDOVER PASS on the later attempt; `DEMO-EQ-006` retained a failed handover before a later success. Reports can be found by plan or paged by status; the seed has two FINAL reports. Repository methods do not decide completion or compose report content.

## 16. Status History Queries

Plan and item history use separate typed FK queries ordered by `actionTimestamp, id`; actor history is pageable. The repair hand-off item's final history event was REPAIR_REQUIRED with a reason. No generic target abstraction or history mutation method was introduced.

## 17. UC12 Equipment History Query Path

```text
Equipment by code
  → page plan items with parent plans
  → for each item: ordered attempts + item history
  → for each attempt: ordered progress logs + acceptance records
```

The DS-09 integration test assembled `DEMO-EQ-004` across **2 plans**, **3 attempts**, work logs, acceptance records and item history using **12 focused repository calls**. It preserved the earlier COMPLETED result and later REPAIR_REQUIRED hand-off. No giant joined graph or API DTO was created.

## 18. Lazy Loading / Fetch Strategy

All entity relationships remain LAZY from Phase 2.2. List methods fetch only necessary to-one relations through `@EntityGraph`; plan detail uses one `JOIN FETCH`. Tests checked selected related objects were already initialized. Unrequested relation graphs remain lazy, and future services should assemble data within an appropriate transaction.

## 19. N+1 Problem

N+1 occurs when one list query is followed by a separate relation query for each row; for example, 20 equipment plus 20 department queries. That multiplies SQL as the page grows. The selected fetch plans kept the measured list scenarios at two SQL statements each, including page/count SQL.

## 20. Query Audit

[`phase_2_3_query_audit.md`](phase_2_3_query_audit.md) records built-in Hibernate prepared-statement counts on the seeded PostgreSQL database:

| Scenario | Rows | SQL statements | Result |
| --- | ---: | ---: | --- |
| Equipment + department | 20 | 2 | PASS |
| Plan items + equipment | 12 | 2 | PASS |
| Pending approvals + display relations | 2 | 2 | PASS |

These are focused N+1 checks on demo data, not a latency or production-scale benchmark.

## 21. Index Alignment

Existing indexes support equipment code, item `(equipment_id, plan_id)`, item `(plan_id, status)`, request `(status, request_type, submitted_at)`, execution `(plan_item_id, attempt_no)`, progress `(execution_id, event_at)`, acceptance `(execution_id, acceptance_type)`, and typed history timestamp access. **PERFORMANCE CANDIDATES:** date-applicable coverage, equipment-by-department paging and recent-plan sorting have no dedicated frozen indexes. Their benefit should be measured later; no index or migration was added now.

## 22. Repository Integration Tests

| Test area | Purpose | Result |
| --- | --- | --- |
| 14 repository beans and master lookups | Spring wiring and seed references | PASS |
| Equipment paging/department fetch | Count, page size, order and display relation | PASS |
| Plan and item queries | Status, plan detail and multi-plan device history | PASS |
| Coverage evidence | FREE and UNKNOWN lookup by device/date | PASS |
| Approval queues/rounds | Pending types, pages, retained actions | PASS |
| Work, acceptance, report and history | Attempts and chronological evidence | PASS |
| DS-01/07/08 and DS-02/05 | Contract, unknown, pending, external and handover evidence | PASS |
| UC12 assembly | Two campaigns and three attempts | PASS |
| SQL-count audit | Three selected N+1 cases | PASS |

The new Phase 2.3 suite ran **10 tests, 0 failures** against real PostgreSQL. Tests read the seed; they create no separate fixture and perform no permanent writes.

## 23. Dataset Validation

The tests used stable semantic codes and the Phase 1.3 scenario catalog: DS-01 FREE completion, DS-02 external provider, DS-03 revision rounds, DS-04 technical rework, DS-05 handover failure, DS-06 repair hand-off, DS-07 UNKNOWN coverage, DS-08 pending vendor, and DS-09 UC12 multi-campaign history. Seed totals after all testing remained 8 departments, 15 users, 40 equipment, 7 providers, 35 coverage rows, 8 plans, 52 items, 19 requests, 15 actions, 30 executions, 90 logs, 40 acceptances, 4 reports and 240 history rows: **603 total**.

## 24. Phase 2.2 Regression

The three Phase 2.2 tests passed: real association/enum loads, rollback-only version increments, and the complete metadata audit. That audit still reports **14 entities, 117 mapped columns, 31 FK associations, 11 exact enum vocabularies and two `@Version` fields**. Hibernate schema validation passed; the packaged server's health endpoint returned `UP`. Four earlier Phase 2.1 tests also passed.

## 25. Database Regression

A guarded reset affected only `medical_maintenance_backend_dev`. The packaged application applied Flyway V001–V006 on the empty database, the original Phase 1.3 seed was reloaded, and `mvn clean test` passed. Final metadata remained **14 business tables / 117 columns / 31 FKs / 6 migrations / 603 demo rows**. No V007 or index migration was created; frozen SQL, entity mappings and source PDFs were not edited.

## 26. Problems Found

The first repository test compile had malformed parentheses in two test assertions; repository code itself started and parsed successfully. The source prompt also used `ServiceProvider.findByCode` as an example, but the frozen provider table has no code field, so that method would contradict the schema.

## 27. Fixes Applied

Test assertions were corrected, then the complete suite passed, including a clean rebuild. Provider reference access uses the actual frozen fields (`id`, `name`, `active`); no speculative `code` column or mapping was added.

## 28. What Has NOT Been Implemented

No service layer, DTO, REST controller, security/authentication, RBAC, report generation, business transition, approval command, custom modifying query, bulk delete, or frontend code was added. Inherited `JpaRepository` save/delete methods exist technically, but this phase exposes no service or API that uses them.

## 29. Phase 2.4 Handoff

API foundation can rely on 14 repository beans, paged and sorted list methods, explicit fetch plans, ordered evidence queries and a proven UC12 assembly path. The API should introduce DTOs and parameter validation, keep entities out of HTTP responses, and let later services own business and authorization decisions. The [repository query guide](../../backend/docs/repository-query-guide.md) documents method choices and limits.

## 30. Final Status

**PASS.** All 14 repositories exist; all required query groups have database-backed tests; selected list fetches avoid obvious N+1 behavior; UC12 is assembled from focused reads; **17/17** total backend tests pass; health is `UP`; schema and 603-row seed remain unchanged.

## 31. Slide-ready Summary

- Phase 2.3 adds 14 thin Spring Data repositories over the frozen JPA model.
- Main lookups, paged lists and chronological evidence queries are verified on 603 demo rows.
- Explicit fetch plans keep three measured list scenarios to 2 SQL statements each.
- UC12 history retrieves one device across two campaigns and three attempts.
- Clean rebuild, 17/17 tests and health check pass; schema remains 14/117/31.
