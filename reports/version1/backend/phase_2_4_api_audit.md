# Phase 2.4 API Audit

Evidence: `ApiIntegrationTest`, `ApiExceptionHandlerTest`, and `ApiQueryAuditTest` against the Phase 1.3 PostgreSQL seed. Query counts use Hibernate prepared-statement statistics cleared before each HTTP request; they include one account reload and pageable count SQL.

## Endpoint Audit

| Endpoint | Success | Invalid Input | Not Found | Pagination | Result |
| --- | --- | --- | --- | --- | --- |
| GET `/api/departments` | 200 / 8 rows | N/A | N/A | No | PASS |
| GET `/api/equipment` | 200 / 40 total | 400 | N/A | Yes | PASS |
| GET `/api/equipment/{id}` | 200 | 400 for nonpositive ID | 404 | No | PASS |
| GET `/api/plans` | 200 / 8 total | 400 | N/A | Yes | PASS |
| GET `/api/plans/{id}` | 200 | 400 for nonpositive ID | 404 | No | PASS |
| GET `/api/plans/{planId}/items` | 200 | 400 for invalid page | 404 | Yes | PASS |
| GET `/api/approvals/pending` | 200 / 3 total | 400 | N/A | Yes | PASS |
| GET `/api/providers` | 200 / 7 rows | N/A | N/A | No | PASS |

## DTO Audit

| DTO | Entity Leakage | Sensitive Fields | Nested Graph | Result |
| --- | --- | --- | --- | --- |
| DepartmentResponse | None | None | None | PASS |
| EquipmentResponse | None | None | None | PASS |
| MaintenancePlanResponse | None | None | None | PASS |
| MaintenancePlanItemResponse | None | None | None | PASS |
| ApprovalRequestResponse | None | None | None | PASS |
| ServiceProviderResponse | None | None | None | PASS |

## Error Audit

| Error Type | HTTP Status | Code | Safe Message | Result |
| --- | ---: | --- | --- | --- |
| Bean validation | 400 | `VALIDATION_ERROR` | Yes | PASS |
| Invalid parameter / enum / sort | 400 | `INVALID_PARAMETER` | Yes | PASS |
| Missing resource | 404 | `RESOURCE_NOT_FOUND` | Yes | PASS |
| Unsupported method | 405 | `METHOD_NOT_ALLOWED` | Yes | PASS |
| Data integrity (handler test) | 409 | `DATA_CONFLICT` | Yes | PASS |
| Unexpected error (handler test) | 500 | `INTERNAL_ERROR` | Yes | PASS |

## Query Audit

| Endpoint | Rows | SQL Statements | N+1 | Result |
| --- | ---: | ---: | --- | --- |
| `GET /api/equipment` | 20 | 3 | No obvious expansion | PASS |
| `GET /api/plans/{planId}/items` | 3 | 4 | No obvious expansion | PASS |
| `GET /api/approvals/pending` | 2 | 3 | No obvious expansion | PASS |

This checks three representative demo-data pages, not production latency or scale. Successful JSON responses contain DTO fields, not Hibernate proxies or password hashes.
