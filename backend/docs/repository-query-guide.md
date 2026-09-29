# Repository & Query Guide

## What is a Repository?

A repository is the data-access interface between later services and JPA entities. Spring Data creates its implementation at startup. Each of the 14 repositories extends `JpaRepository<Entity, Long>`, which supplies basic lookup and persistence methods; named read methods below cover the frozen V1 query paths.

## Why Spring Data JPA?

The validated entity mappings already describe tables and FKs. Spring Data JPA can generate straightforward queries from method names and execute focused JPQL, while Hibernate handles SQL and object loading. The repository layer adds no business service behavior; later Phase 2.4/2.5 controllers and security call these reads.

## Repository Inventory

| Repository | Primary read use |
| --- | --- |
| DepartmentRepository | Unit code lookup |
| UserAccountRepository | Username, role, department lookup |
| EquipmentRepository | Device code, department, active and paged list |
| ServiceProviderRepository | Active provider list |
| MaintenanceCoverageRepository | Dated evidence for one device |
| MaintenancePlanRepository | Paged/status plans, detail with creator |
| MaintenancePlanItemRepository | Plan, status, department and device history |
| ApprovalRequestRepository | Paged director queue and approval rounds |
| ApprovalActionRepository | One terminal action for a request |
| MaintenanceExecutionRepository | Ordered attempts and latest attempt |
| MaintenanceProgressLogRepository | Ordered work notes |
| AcceptanceRecordRepository | Ordered assessments and type lookup |
| MaintenanceReportRepository | Plan report and status list |
| StatusHistoryRepository | Ordered plan/item transitions and actor history |

## Query Strategy

Most methods are **derived queries**: Spring Data reads `findByEquipment_Id` as a filter on the related equipment ID. `MaintenanceCoverageRepository` uses JPQL to filter evidence valid on a supplied date and order dated evidence before undated evidence; it does not decide a maintenance route. `MaintenancePlanRepository.findWithCreatorById` uses one JPQL `JOIN FETCH`. `@EntityGraph` is used for list/detail queries that need a few to-one relations. There are **no native SQL** or `@Modifying` repository queries.

## Pagination

`Pageable` carries page number, size and sorting. It is available for device lists, plans, plan items, approval queues, reports and actor history. A `Page` also reports total elements; a count SQL may be issued. Phase 2.4 API code validates page parameters and limits size to 100. Small per-parent evidence collections, such as attempts and logs for one execution, are returned as ordered lists.

## Sorting

Callers should supply deterministic `Sort` values for pageable methods, including `id` as a tie breaker. Useful examples: equipment `equipmentCode ASC, id ASC`; plans `createdAt DESC, id DESC`; approval queue `submittedAt ASC, id ASC`; device plan items `plan.periodStart DESC, plan.id DESC, id DESC`. Chronological child methods already specify timestamp and `id` order in their names.

## Lazy Loading and Fetch Plans

All entity FKs remain LAZY. `EquipmentRepository.findAllBy` fetches department; plan-item list methods fetch equipment; approval queue methods fetch creator, proposal, subject and subject equipment. These are to-one associations, so paging stays bounded by root rows. A repository method that does not declare a fetch plan leaves other relations lazy. Later services should compose repository results inside a transaction and request only data a screen needs.

## N+1

N+1 means loading one page and then issuing a separate SQL statement for each row's related data. Built-in Hibernate statistics measured 2 SQL statements for 20 equipment plus department, 2 for 12 plan items plus equipment, and 2 for 2 pending approvals plus display relations. See [query audit](../../reports/backend/phase_2_3_query_audit.md). This checks obvious list expansion on the demo dataset, not production latency.

## Important Query Paths

- **Equipment list:** `EquipmentRepository.findAllBy` or `findByDepartment_Id`, with a caller-supplied page and sort. Department is fetched for display.
- **Approval queue:** `ApprovalRequestRepository.findByStatus` or `findByStatusAndRequestType` with `PENDING` and a page ordered by submission time. Related display fields are fetched; actions are read separately by request ID.
- **Plan detail:** load plan with creator, then page its items by plan ID and optionally status. The item query fetches equipment and current assignment fields.
- **Coverage:** read all evidence or date-applicable evidence for a device. UNKNOWN/absent evidence is returned as data; route validation belongs to services.
- **UC12 history:** find device by code → page its plan items with parent plans → for each item, read ordered attempts and item history → for each attempt, read ordered logs and acceptances. The `DEMO-EQ-004` test assembled two campaigns and three attempts in 12 focused repository calls, without a giant object graph or API DTO.

## Index Alignment

The frozen indexes support code lookup, item `(equipment_id, plan_id)` history, item `(plan_id, status)` lists, approval `(status, request_type, submitted_at)` queue, execution `(plan_item_id, attempt_no)`, progress `(execution_id, event_at)`, acceptance `(execution_id, acceptance_type)`, and history `(target_id, action_timestamp)` queries. **PERFORMANCE CANDIDATES:** date-applicable coverage, equipment-by-department paging, and recent-plan sorting do not have dedicated frozen indexes. Measure later with realistic volume before proposing an index migration; Phase 2.3 changes no schema.

## Write / Delete Restrictions

`JpaRepository` technically exposes `save` and `delete`. This phase adds no custom updates or deletes. Official approvals, attempts, acceptances and history must be retained under the frozen policy; later services must not expose inherited destructive methods casually. Repository tests read the 603-row seed and leave it unchanged.

## What belongs in Phase 3 instead

Plan edits, approvals, coverage routing, assignment, completion, history writes, authorization and multi-record transactions are business decisions. Repositories only return persisted evidence. Phase 2.4 added DTOs/controllers and validation without returning entities; Phase 2.5 added authentication. Business decisions remain Phase 3 work.
