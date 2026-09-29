# Phase 2.5 Security Audit

**Date:** 2026-09-25. **Result:** PASS. Evidence: `SecurityIntegrationTest` (7 HTTP/DB tests), Phase 2.4 HTTP tests (11), complete 35-test backend suite, packaged JAR smoke test, database metadata query and source/log review. The isolated `medical_maintenance_backend_dev` was reset, migrated, seeded and given only four demo password-hash updates before validation.

## Authentication Audit

| Scenario | Expected | Result |
| --- | --- | --- |
| Four synthetic users log in | 200, JWT and exact role/user ID | PASS — all four |
| `GET /api/auth/me` with token | 200, minimal current user and department | PASS — all four |
| Wrong password / unknown user | Same 401 `INVALID_CREDENTIALS` | PASS |
| Inactive login | 401 `INVALID_CREDENTIALS` | PASS; restored account |
| Anonymous `/api/auth/me` and equipment | 401 `AUTHENTICATION_REQUIRED` JSON | PASS |
| Health without token | 200 `UP` | PASS |
| Disabled user after token issuance | 401 immediately | PASS; restored account |

## Authorization Audit

| Role | Endpoint | Expected | Result |
| --- | --- | --- | --- |
| `BAN_GIAM_DOC` | `/api/approvals/pending` | 200 | PASS |
| `PHONG_VTYT` | `/api/approvals/pending` | 403 `ACCESS_DENIED` | PASS |
| `KHOA_PHONG` | `/api/approvals/pending` | 403 `ACCESS_DENIED` | PASS |
| `ADMIN` | `/api/approvals/pending` | 403 `ACCESS_DENIED` | PASS |
| `KHOA_PHONG` | `/api/equipment` | 200 authenticated | PASS |
| `ADMIN` | `/api/plans` | 200 authenticated | PASS |
| Changed current role after issuance | DB role governs access, stale claim ignored | PASS; restored role |

## JWT Audit

| Check | Result |
| --- | --- |
| HMAC signature | Signed and verified with JJWT 0.13.0; tampered signature returns 401 |
| Expiration | 3600 seconds; deliberately expired signed token returns 401 |
| Invalid token | Malformed token and invalid signature return `INVALID_TOKEN`, not 500 |
| Minimal claims | Only `sub`, `role`, optional `departmentId`, `iat`, `exp` verified |
| Secret externalized | Base64 environment value, at least 32 random bytes; no source fallback |
| User reload | One DB lookup per authenticated request; current active flag/role used |

## Credential Audit

| Check | Result |
| --- | --- |
| BCrypt | Four selected demo hashes use `$2b$12$`; Spring encoder matches correct password and rejects wrong one |
| Plaintext database password | None; `password_hash` holds BCrypt digest |
| Committed secret | None; generated credentials and JWT key are ignored under `.local-postgres/`, mode 600 |
| Inactive user rejection | Login denied; previously issued token denied after deactivation |
| Database target | Bootstrap permits only `medical_maintenance_backend_dev` on local port 55432 as `ltnc_backend_dev` |

## Endpoint Exposure Audit

| Path | Anonymous | Authenticated | Result |
| --- | --- | --- | --- |
| `POST /api/auth/login` | 200 with valid demo credentials | 200 | PASS |
| `GET /actuator/health` | 200 | 200 | PASS |
| `GET /api/auth/me` | 401 | 200 | PASS |
| Existing equipment/plan/reference GET | 401 | 200 | PASS |
| `GET /api/approvals/pending` | 401 | 200 for director, 403 otherwise | PASS |

## Regression and data audit

The full suite passed **35 tests, 0 failures, 0 errors** after clean reset/seed/bootstrap. Phase 2.4 preserved its 9 HTTP integration tests, one query audit and one handler test. Hibernate HTTP counts for 20 equipment, 3 plan items and 2 pending approvals were **3, 4 and 3 SQL statements** including one account reload each; see [API audit](phase_2_4_api_audit.md). The packaged JAR passed health, four-role login, `/me`, authenticated equipment and queue allow/deny checks. PostgreSQL remains **14 business tables / 117 columns / 31 FKs / 6 successful migrations / 603 demo rows**. No migration or seed source changed.

Searches of app/test logs found no generated default Spring password, JWT secret, demo password variable value or Bearer token. Token and password values were not printed by the HTTP smoke test. No CORS wildcard or additional Actuator exposure is configured.
