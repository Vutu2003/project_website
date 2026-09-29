# Phase 2.4 — REST API Foundation, DTO & Validation

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-25  
**Status:** **PASS**

## 1. Objective

Provide a small, read-only HTTP contract over the validated repository layer. The result demonstrates entity-to-DTO mapping, paged/filterable GET endpoints, input validation, safe errors and predictable SQL. It does not implement maintenance decisions or authentication.

## 2. Starting Point

Phase 2.1 supplied Spring Boot, Flyway, PostgreSQL and health. Phase 2.2 validated 14 JPA entities against 117 columns and 31 FKs. Phase 2.3 supplied 14 repositories and query/fetch plans. The backend had no business-facing HTTP API before this phase; the Phase 1.3 seed supplied 603 synthetic rows for integration tests.

## 3. What is a REST API?

A REST API lets a client read a resource through an HTTP path and method. In this project, eight `/api` paths use `GET` and return JSON for existing equipment, plans, approval queues and reference data. A `POST` to these read-only resources is not a disguised workflow action; it receives HTTP 405.

## 4. Backend Layering

```text
HTTP request
    ↓
Controller — validates parameters and chooses a read
    ↓
Repository — queries existing records
    ↓
JPA / Hibernate — maps SQL rows to entities
    ↓
PostgreSQL — frozen schema

Entity → explicit mapper → response DTO → JSON
```

A **controller** is the HTTP entry point. Each endpoint needs one focused repository read, except plan-items also checks the parent plan exists; no application facade was needed. Business services and authorization will be inserted in later phases.

## 5. What is a DTO?

A Data Transfer Object is a small Java shape for data crossing the HTTP boundary. `PageQuery` is the request DTO for page/size/sort input. Six response DTOs select useful fields from Department, Equipment, MaintenancePlan, MaintenancePlanItem, ApprovalRequest and ServiceProvider. They define what this API promises independently of table columns.

## 6. Why Entities Are Not Returned Directly

JPA entities include technical and sensitive fields plus LAZY relationships. Returning them directly could expose password hashes, trigger loading during JSON serialization, or recursively serialize object graphs. Controllers return response records or `PageResponse<T>` only. Tests inspected JSON from all response groups and found no Hibernate proxy fields, password hash, technical specification or nested entity graph.

## 7. Package Structure

`vn.edu.medmaintenance.api.controller` contains four small controllers; `api.dto.request` contains `PageQuery`; `api.dto.response` contains six resource records plus page/error records; `api.mapper` contains five one-way mapper classes; `api.common` contains bounded pagination/sort input handling; `api.exception` contains the two simple exceptions and global handler. No security or business service package was added.

## 8. API Conventions

The base path is `/api`, with no premature `/api/v1` prefix. Responses are JSON with plain DTO/list/page success bodies. `200` means a successful GET; invalid input is `400`, missing resources `404`, unsupported methods `405`, a generic future write conflict `409`, and unexpected failures `500`. An **HTTP status code** lets clients distinguish success from failure without interpreting a custom `success` flag.

## 9. Endpoint Inventory

| Method | Path | Purpose | Pagination |
| --- | --- | --- | --- |
| GET | `/api/departments` | Department reference list | No |
| GET | `/api/equipment` | Equipment list and filters | Yes |
| GET | `/api/equipment/{id}` | Equipment detail | No |
| GET | `/api/plans` | Plan list and status filter | Yes |
| GET | `/api/plans/{id}` | Plan detail | No |
| GET | `/api/plans/{planId}/items` | Items in a plan | Yes |
| GET | `/api/approvals/pending` | Pending director queue | Yes |
| GET | `/api/providers` | Active provider list | No |

## 10. Equipment API

The list supports optional `departmentId`, `active`, page, size and an allowlisted sort. Neutral repository reads were added for equipment ID with department and for active plus department filtering; existing query meanings were not changed. Detail uses `DEMO-EQ-004` via its repository-discovered ID in tests. `EquipmentResponse` exposes identity, model/serial and flat department information; it omits `technicalSpec`. The frozen table has no manufacturer column, so the illustrative manufacturer field was not invented.

## 11. Plan / PlanItem API

Plans are paged and optionally filtered by exact eight-state `PlanStatus`. Plan detail includes dates, status, creator name and version without embedding a user entity. Items are paged under an existing plan and expose equipment, historical department, current provider/route and item status as selected flat values. The endpoint checks the parent plan and returns 404 when it is absent; it does not decide whether an item may change state.

## 12. Approval Queue API

The queue returns only stored `PENDING` requests and may filter by `PLAN_APPROVAL` or `VENDOR_SELECTION`. The seed has one pending plan and two pending vendor requests. The DTO includes creator, typed target and proposed provider display fields without exposing an entity graph. There is no decision or mutation endpoint.

## 13. Reference APIs

`/api/departments` returns 8 code-sorted units. `/api/providers` returns 7 active name-sorted providers. Both return concise records, with no management/write API. The seed is synthetic and is not a real hospital directory.

## 14. Pagination

A **pagination response** splits a growing result into pages. `PageResponse<T>` exposes `content`, `page`, `size`, `totalElements`, `totalPages` and `last`, rather than Spring's framework-specific `Page` JSON. Defaults are `page=0`, `size=20`; Bean Validation requires `page >= 0` and `1 <= size <= 100`. Tests verified 40 equipment over four size-10 pages, item pages, approval pages and error responses for invalid limits.

## 15. Sorting

**Sorting** sets the order of list rows. `PageRequests` accepts one `sort=field,asc|desc` value and adds `id` as a stable tie breaker. Equipment allows `equipmentCode`, `name`, `model`, `id`; plans allow `createdAt`, `periodStart`, `status`, `id`; items allow `id`, `plannedDate`, `status`; approvals allow `submittedAt`, `id`. Unlisted fields, including internal nested properties, return 400 before reaching Spring `Sort`.

## 16. Filtering

Equipment filters by stored department ID and active flag. Plans filter by `PlanStatus`; pending approvals filter by `ApprovalRequestType`. Exact enum names are used in HTTP and JSON, such as `APPROVED` or `VENDOR_SELECTION`. A bad enum value produces a structured 400, not a server error. Filtering is data selection, not department authorization.

## 17. Validation

**Validation** rejects malformed input before querying. Jakarta Bean Validation on the `PageQuery` request DTO checks page and size; additional API-boundary checks reject nonpositive IDs and invalid sort syntax/fields. An invalid page yields `VALIDATION_ERROR` with a field error. These checks do not decide whether a plan may be approved, coverage is sufficient or maintenance may start; those are Phase 3 business rules.

## 18. Error Model

An **error response** has stable fields so clients can display or diagnose failures without seeing Java internals. Example:

```json
{
  "timestamp": "2026-09-25T15:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/equipment",
  "fieldErrors": [{"field": "size", "message": "must be less than or equal to 100"}]
}
```

The example timestamp illustrates ISO-8601 format; runtime responses use the actual UTC time. No success/error universal wrapper or HTTP-200 error payload was introduced.

## 19. Global Exception Handling

An **exception handler** turns a Java exception into a controlled HTTP response. `@RestControllerAdvice` handles Bean Validation, type/enum mismatch, missing parameters, custom invalid input, missing resource/path, unsupported method, data integrity and an unexpected-error fallback. Client bodies omit stack traces, SQL and package names; server logs retain technical failures. The 409 and 500 branches were exercised directly in handler tests because this phase has no write endpoint to trigger them over HTTP.

## 20. DTO Mapping Strategy

A **mapper** explicitly copies selected entity values to a response DTO. Five plain Java mapper classes handle the six resource DTOs; `PlanMapper` covers plan and item. This is one-way entity-to-response mapping, with no MapStruct or generic framework. Flat ID/code/name fields avoid nested entity serialization; fetch plans from Phase 2.3 provide the related data needed with `open-in-view=false`.

## 21. Date/Time and Enum Serialization

Jackson serializes `LocalDate` as ISO `YYYY-MM-DD` and `OffsetDateTime` as an ISO offset timestamp. Enum names are JSON strings, never numeric ordinals. Integration tests parsed a plan period as `LocalDate`, its creation time as `OffsetDateTime`, and the status string `APPROVED`.

## 22. Integration Tests

| Scenario | Expected | Result |
| --- | --- | --- |
| Health and department/provider lists | UP, 8 units, 7 active providers | PASS |
| Equipment list and filters | 40 total; 8 in Hồi sức; active filter | PASS |
| Pagination and allowed sort | Size, totals, distinct pages and order | PASS |
| Equipment detail | Correct `DEMO-EQ-004` DTO | PASS |
| Plan list/filter/detail | 8 total, 1 APPROVED, ISO dates | PASS |
| Plan items | Paged item DTOs under a real plan | PASS |
| Pending approvals | 1 plan and 2 vendor requests | PASS |
| DTO JSON | No entity graph or credential fields | PASS |
| HTTP query audit | Three bounded SQL samples | PASS |

The new Phase 2.4 suite ran **11 tests, 0 failures**: nine HTTP integration methods, one API query audit and one handler test.

## 23. Error Tests

Negative HTTP tests covered page below zero, size zero/above 100, disallowed sort field/direction, bad department ID, bad boolean/enum, missing and nonpositive resource IDs, missing plan items parent, unknown API path, and unsupported POST. They returned structured 400, 404 or 405 as appropriate. Direct handler tests verified safe 409 and 500 bodies.

## 24. JSON Leakage Audit

Success JSON for departments, equipment, plans, items, pending approvals and providers was inspected. It contained no `hibernateLazyInitializer`, `handler`, password hash/digest, `technicalSpec`, or nested `createdByUser`, `department` or `plan` entity object. Controllers expose no JPA entity return type. The [API audit](phase_2_4_api_audit.md) records the six DTO groups.

## 25. N+1/API Query Check

Hibernate prepared-statement statistics were cleared before each actual HTTP request. The measured results were: 20 equipment plus department **2 SQL**, 3 plan items plus equipment/department/provider **3 SQL** (including parent existence), and 2 pending approvals plus display relations **2 SQL**. DTO mapping did not cause per-row lazy queries. This is an obvious-N+1 check on the demo seed, not a production latency or scale claim.

## 26. Phase 2.3 Regression

All 10 Phase 2.3 repository tests passed unchanged, including the UC12 two-campaign retrieval and repository SQL-count audit. All three Phase 2.2 persistence tests and four Phase 2.1 foundation tests also passed. Flyway validation, Hibernate schema validation and Actuator health remained healthy.

## 27. Database Regression

After a guarded reset of only `medical_maintenance_backend_dev`, the packaged app applied the original Flyway V001–V006 on an empty DB. The Phase 1.3 seed was reloaded, `mvn clean test` passed **28/28**, and the packaged JAR again returned health `UP`. Manual curl checked departments (8), equipment (40), approved plans (1), pending vendor requests (2) and invalid size (400). Final database metadata remained **14 business tables, 117 columns, 31 FKs, 6 migrations and 603 demo rows**. No SQL migration, seed or entity mapping was changed.

## 28. Problems Found

The first unsupported-method HTTP test returned Spring's default 405 body rather than the standard API error body. The original advice was scoped to the controller package; a 405 occurs before Spring selects a controller, so that scoped advice did not receive the exception. No database or JPA defect was found.

## 29. Fixes Applied

The exception advice was made global and given a dedicated 405 handler. The same test then received `METHOD_NOT_ALLOWED` in the stable error shape; all tests passed. A missing equipment manufacturer field in the illustrative prompt was handled by using only the actual frozen columns, without adding schema or DTO placeholders.

## 30. What Has NOT Been Implemented

No authentication, Spring Security, RBAC, department authorization, business service, workflow command, approval decision, state transition, report generation, UC12 HTTP presentation or frontend change was added. All eight endpoints are read-only. The API is a technical foundation, not a completed maintenance workflow.

## 31. Phase 2.5 Handoff

Security work can secure `/api/**` around the existing DTO-based read paths and consistent error contract. The user account entity and role enum already exist, but no current endpoint authenticates a user or restricts department scope. Phase 2.5 must define those controls before treating these paths as an authorized hospital API.

## 32. Final Status

**PASS.** Eight read-only endpoints, six resource response DTOs, validated pagination/sort/filter input, standardized safe errors and bounded SQL query shapes are verified. **11/11** new tests and **28/28** full backend tests pass; health is `UP`; schema and 603-row seed remain unchanged.

## 33. Slide-ready Summary

- Phase 2.4 adds eight read-only `/api` endpoints over the 14-repository foundation.
- Six response DTOs keep entities, credential fields and lazy graphs out of JSON.
- Validation and allowlisted sorting return structured 400 errors; missing records return 404.
- Three HTTP list checks use 2, 3 and 2 SQL statements without obvious N+1 growth.
- Clean rebuild, 28/28 tests and health pass; database remains 14/117/31 with 603 demo rows.
