> Current Phase 2 foundation: these business API reads require a JWT Bearer token; see [security-guide.md](security-guide.md).

# API Foundation Guide

## What is REST?

A REST API exposes resources through HTTP methods and paths. These Phase 2.4 paths use `GET` to read the frozen demo data and return JSON. No write or workflow endpoint is available yet.

## Controller → Repository/Data Flow

```text
HTTP GET → controller → repository → JPA/Hibernate → PostgreSQL
                    ↘ explicit mapper → response DTO → JSON
```

A controller handles the HTTP boundary and chooses a focused repository read. DTO mapping uses the repository's explicit fetch plan; `spring.jpa.open-in-view=false` remains active. Business services are Phase 3 work; the current high-level authentication/RBAC boundary is described in the security guide.

## Why DTOs?

A response DTO is a small Java record containing only the fields promised to HTTP clients. It prevents a JPA entity, password hash, Hibernate proxy, or recursive relationship graph from becoming part of the API contract. Six response DTOs cover departments, equipment, plans, plan items, approval requests and providers. `PageQuery` is the request DTO for page, size and sort parameters. Plain Java mapper classes convert entities to responses.

## Endpoint Summary

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/departments` | Unit reference list |
| GET | `/api/equipment` | Paged device list |
| GET | `/api/equipment/{id}` | Device detail |
| GET | `/api/plans` | Paged plan list |
| GET | `/api/plans/{id}` | Plan detail |
| GET | `/api/plans/{planId}/items` | Paged items within a plan |
| GET | `/api/approvals/pending` | Paged pending director queue |
| GET | `/api/providers` | Active provider reference list |

The base path is `/api`; a `/api/v1` prefix is not needed for the current university V1. The seed supplies 8 departments, 40 equipment, 8 plans, 52 items, 3 pending requests and 7 active providers.

## Pagination

Paged endpoints return `{content, page, size, totalElements, totalPages, last}`. The request defaults are `page=0` and `size=20`. Bean Validation requires `page >= 0` and `1 <= size <= 100`. Empty pages have an empty `content` array with the correct totals.

## Sorting

Use one `sort=field,asc` or `sort=field,desc` parameter. The direction may be omitted; `id` is added as a stable tie breaker. Allowed fields:

| Endpoint | Fields | Default |
| --- | --- | --- |
| Equipment list | `equipmentCode`, `name`, `model`, `id` | `equipmentCode,asc` |
| Plan list | `createdAt`, `periodStart`, `status`, `id` | `createdAt,desc` |
| Plan items | `id`, `plannedDate`, `status` | `id,asc` |
| Pending approvals | `submittedAt`, `id` | `submittedAt,asc` |

An unknown field or direction returns HTTP 400. Internal properties cannot be passed through to Spring `Sort` unchecked.

## Filtering

`/api/equipment` accepts optional `departmentId` and `active=true|false`. `/api/plans` accepts an exact `PlanStatus` value, such as `APPROVED`. `/api/approvals/pending` accepts `requestType=PLAN_APPROVAL|VENDOR_SELECTION`. Invalid enum text or a nonpositive department ID returns 400. These filters select stored rows; they do not decide who may see them.

## Validation

`PageQuery` uses Jakarta Bean Validation for page and size. Controllers also reject nonpositive IDs and the sort helper checks allowlists. This is input validation only. Approval, provider routing, maintenance start and acceptance rules belong to Phase 3.

## Error Format

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

`@RestControllerAdvice` creates this format for validation, invalid parameters, missing resources, unsupported methods, data conflicts and unexpected errors. The Phase 2.5 security entry point/access-denied writer uses the same shape for 401/403. Responses omit stack traces, SQL and internal Java class names. The timestamp uses ISO-8601 UTC; database `LocalDate`/`OffsetDateTime` and enum names use normal Jackson ISO/string JSON output.

## HTTP Status Codes

| Status | Meaning in this foundation |
| ---: | --- |
| 200 | Successful GET or login |
| 401 | Missing/invalid authentication or failed login |
| 403 | Authenticated role lacks route access |
| 400 | Invalid parameter or validation failure |
| 404 | Requested resource/path missing |
| 405 | Unsupported HTTP method |
| 409 | Generic data conflict handler, for later write paths |
| 500 | Unexpected error with safe client message |

Success returns a resource DTO, a list of reference DTOs, or `PageResponse<T>`; there is no universal success wrapper.

## DTO Mapping

`DepartmentMapper`, `EquipmentMapper`, `PlanMapper`, `ApprovalRequestMapper` and `ServiceProviderMapper` are one-way entity-to-response mappers. Related values are flattened to IDs, codes and names rather than nested entity objects. The equipment response omits `technicalSpec`; plan and approval responses omit credential fields and unrelated graphs.

## What Is Not Implemented Yet

Phase 2.5 added JWT authentication and a director-only pending queue restriction around these read endpoints. Complete department authorization, business services, workflow commands, report generation, UC12 HTTP response and frontend remain later work. The [API audit](../../reports/backend/phase_2_4_api_audit.md) records endpoint/error checks and measured SQL counts on the synthetic seed.
