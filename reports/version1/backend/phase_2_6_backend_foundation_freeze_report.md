# Phase 2.6 — Backend Foundation Integration Audit & Freeze

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-26  
**Status:** **PASS**

## 1. Objective

Audit Phases 2.1–2.5 end to end, correct foundation defects, rebuild the isolated dev database from zero and freeze a dependable starting point for Phase 3. No business workflow feature was added.

## 2. What Does “Foundation Freeze” Mean?

A freeze is a reviewed baseline: later work can extend it but should not casually change its schema ownership, DTO/error shape, security identity or layer direction. A **regression test** repeats an old behavior check after a change. A **clean rebuild** starts with an empty dev DB, applies the six migrations, loads the seed, configures demo logins and runs the app/tests. This catches hidden manual setup steps.

## 3. Starting Point

Phase 2.1 provided Spring Boot/PostgreSQL/Flyway; 2.2 mapped all 14 entities; 2.3 added 14 repositories; 2.4 added eight read-only GET routes and DTO/error conventions; 2.5 added four-role JWT security. The baseline Git status showed the project tree as untracked, so standard `git diff` could not show project file changes. The baseline had 74 Java source files, 9 test classes and 35 test methods before this audit's response DTO addition.

## 4. Final Technology Stack

| Technology | Version | Role |
| --- | --- | --- |
| Java / Maven | Temurin 17.0.20.1 / 3.9.11 | Runtime and build |
| Spring Boot | 3.5.16 | HTTP, dependency wiring, Actuator |
| PostgreSQL | 16.15 | Frozen relational store |
| Flyway | 11.7.2 | Exclusive schema migration owner |
| Hibernate ORM | 6.6.53.Final | JPA mapping and validation |
| Spring Security | 6.5.11 | Stateless HTTP security |
| JJWT | 0.13.0 | HMAC JWT creation/validation |

## 5. Final Backend Architecture

```text
Future React → HTTP/JWT → Spring Security → REST controllers
                                      → DTO mapper + read repository
Phase 3 inserts transactional business services before repositories.
Repositories → JPA entities → Hibernate → PostgreSQL
Flyway V001–V006 ────────────────────────────────→ schema
```

The security filter supplies identity, controllers select reads, mappers shape JSON, repositories fetch rows, and Hibernate validates rather than creates tables. Keeping each concern in its layer prevents workflow rules from being duplicated in controllers or SQL.

## 6. Package Structure

Final main source inventory: `persistence` 39 files (14 entities, 11 enums, 14 repositories), `api` 23, `security` 12 and application entry point 1: **75 Java files**. The API has four read controllers and 10 request/response/support DTO records; auth adds `LoginRequest`, `LoginResponse`, `AuthenticatedUserResponse`. The eight business GET routes plus login and `/me` are the only business HTTP surface.

## 7. Database Integration

The dedicated `medical_maintenance_backend_dev` runs on the project-owned PostgreSQL 16 cluster at loopback port 55432. The clean rebuilt backend schema's normalized dump matched the Phase 1 `medical_maintenance_db` schema byte for byte. All 14 tables' canonical seeded rows matched the Phase 1 fixture after excluding user password hashes; exactly four chosen demo hashes differed by design.

## 8. Flyway Ownership

Six unchanged files, V001–V006, are copied byte-identically into the JAR. Flyway owns DDL and records six successful migrations. On restart it validated all six and reported “No migration necessary.” Hibernate stayed at `ddl-auto=validate`, with `open-in-view=false`; it did not create or alter tables.

## 9. Persistence Layer

The mapping audit passed **14/14 entities, 117/117 columns, 31/31 associations and 11/11 constrained enums**. Only `MaintenancePlan` and `MaintenancePlanItem` have `@Version`. There is no JPA entity for Flyway metadata, no globally eager association and no cascade delete of audit evidence. Stale-update translation remains Phase 3 work.

## 10. Repository Layer

All 14 repositories work against the real seed, including UC12 multi-campaign equipment history. They have focused reads and fetch plans, with no custom `@Modifying`/bulk delete method. `JpaRepository` inherited writes/deletes exist technically but no Phase 2 HTTP route exposes them; Phase 3 must preserve official history and approval evidence.

## 11. REST API Foundation

Eight GET paths cover departments, equipment list/detail, plans list/detail/items, pending director requests and active providers. Success uses flat DTOs or `PageResponse`; list defaults are page 0 and size 20, with max 100 and allowlisted sorting plus stable ID ties. Input errors, missing resources and unsupported methods retain the Phase 2.4 JSON contract. No maintenance command route was introduced.

## 12. Security Foundation

Spring Security protects `/api/**` with stateless Bearer JWT. Login and Actuator health are public; the pending director queue requires `BAN_GIAM_DOC`. BCrypt checks existing `user_account.password_hash`. A verified token leads to one account reload per request, so inactive/changed-role accounts take effect immediately. CSRF is disabled for explicit Authorization-header credentials; CORS remains closed pending a concrete frontend origin.

## 13. Authentication Flow

`POST /api/auth/login` verifies a synthetic user's BCrypt hash and issues a one-hour signed token. The JWT filter checks the signature/expiry, reloads the account, then sets an internal `AuthenticatedUser` in `SecurityContext`. `/api/auth/me` maps that principal to a separate public `AuthenticatedUserResponse`. The token has only `sub`, role, optional department ID, `iat` and `exp`; it has no hash or full entity.

## 14. Configuration / Environment Variables

Required at runtime: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`. Local defaults: `DB_HOST=127.0.0.1`, `DB_PORT=55432`, `DB_NAME=medical_maintenance_backend_dev`, `APP_PORT=8080`, `JWT_EXPIRATION_SECONDS=3600` (allowed 60–3600). The local setup scripts generate ignored mode-600 env files. `application.yml` owns shared Flyway/JPA/health/JWT settings; `application-dev.yml` owns JDBC configuration; `.env.example` holds placeholders only. Startup fails if JWT key is missing or too short.

## 15. Clean Rebuild

The reproducible order passed: source toolchain → setup dev DB → generate local security env → guarded reset of backend dev DB → package/bootstrap app once for Flyway V001–V006 → stop → load all six seed SQL files → bootstrap four BCrypt hashes → `mvn clean test` → `mvn clean package` → start final JAR → smoke HTTP. The [README](../../backend/README.md) now gives exact commands; its login snippet was executed successfully against the final JAR. The source migration and seed SHA-256 lists were unchanged across the audit.

## 16. Full Test Regression

| Layer | Tests | Result |
| --- | ---: | --- |
| Phase 2.1 foundation | 4 | PASS |
| Phase 2.2 persistence | 3 | PASS |
| Phase 2.3 repository | 10 | PASS |
| Phase 2.4 API | 11 | PASS |
| Phase 2.5 security + redaction regression | 8 | PASS |
| **Total** | **36** | **0 failures, 0 errors, 0 skips** |

Both `mvn clean test` and an independent `mvn clean package` succeeded after the audit fix. There are no disabled tests. Test reads use stable seed codes/DB lookups where needed; temporary security-test account changes are restored.

## 17. Manual Smoke Test

The final packaged JAR reported health `UP` (200). All four accounts logged in and `/me` returned 200 with exact roles. Authenticated equipment and plans returned totals of 40 and 8. Anonymous equipment and malformed JWT returned 401. BGĐ pending queue returned 200; VTYT, Khoa phòng and Admin returned 403. A browser preflight from `localhost:5173` received no CORS allow-origin header.

## 18. Database Regression

Final metadata: **14 business tables, 117 columns, 31 FKs, six successful migrations, 603 business rows**. Per-table row counts match Phase 1.3: department 8, user 15, equipment 40, provider 7, coverage 35, plan 8, item 52, request 19, action 15, execution 30, progress 90, acceptance 40, report 4 and history 240. Only four demo account hashes were intentionally re-encoded.

## 19. Security Audit

The Phase 2.5 suite still proves correct login, role mapping, BCrypt, minimal JWT claims, invalid/tampered/expired-token 401, inactive-user rejection, immediate role change, 401/403 JSON and no auth bypass. Actual JWT/DB/password values were absent from source, docs and normal logs. Local env files and target JAR are ignored. See [audit matrix](phase_2_6_backend_audit.md).

## 20. API / JSON Leakage Audit

All business controllers return DTOs, never JPA entities. Success JSON excludes hashes, technical specification, Hibernate proxy fields and nested entity graphs. Security responses exclude parser details and stack traces. 400, 404 and 405 were checked through HTTP; generic 409/500 handlers were tested directly because no Phase 2 write path triggers them.

## 21. Query / N+1 Audit

Repository samples remained **2/2/2 SQL**. Actual authenticated HTTP samples were **3 SQL for 20 equipment, 4 for three plan items (including parent existence), and 3 for two pending requests**. Each includes one current-account lookup. These fixed small counts show no obvious per-row expansion on the demo data; they are not production latency measurements.

## 22. Dependency Audit

The POM has the expected Web, Actuator, JDBC, JPA, PostgreSQL, Flyway, Validation, Security, JJWT and test starter set. Spring Boot manages Spring/Hibernate/Flyway/Security versions; the three JJWT modules intentionally share explicit 0.13.0. Dependency tree found no H2, Testcontainers, OAuth2, Lombok or MapStruct. No version upgrade was needed.

## 23. Architecture Audit

Static review found zero persistence→API imports, zero mapper database calls, zero controller SQL/EntityManager/JWT parser usage, and zero custom modifying/delete repository methods. The auth principal is now internal while `AuthenticatedUserResponse` is the wire DTO. Phase 3 services remain the planned transaction and business-rule layer.

## 24. Git / Secret Audit

The baseline repository tree was already untracked, so `git diff` and `git diff --stat` provide no useful per-file review. We instead inspected source/files, compared before/after SQL hashes, matched JAR migration bytes and checked ignored paths. Both source PDF SHA-256 digests still match the Phase 1.2 report; migration and seed files matched pre-audit SHA-256 lists. No commit/push was made. The generated DB password, JWT key and four passwords were not found in backend source or reports.

## 25. Problems Found

Current backend docs still contained old phase-time statements that said authentication or repositories did not exist, and the README did not give one complete clean setup path. The demo bootstrap depended on Python `bcrypt` without an explicit prerequisite check. The login and `/me` endpoints reused the internal security principal record as their public JSON shape. DEBUG-level Spring MVC logging rendered raw login passwords and issued JWTs through autogenerated record `toString()` methods during the first JAR smoke.

## 26. Fixes Applied

The README was reorganized into exact setup/migrate/seed/login/test steps; layer guides and `.env.example` were corrected, and an architecture guide and Phase 3 handoff were added. The bootstrap now fails clearly when Python `bcrypt` is missing. A separate `AuthenticatedUserResponse` preserves existing JSON while removing principal/HTTP coupling. Both login request/response records now redact credentials in `toString()`; a new regression test and an actual DEBUG-level JAR smoke proved raw passwords and tokens absent. Older leaky temporary audit logs were deleted. Full tests and final JAR smoke passed after these changes.

## 27. Known Limitations

There is no business workflow, complete department isolation, refresh token, frontend or formal load test. CORS will be configured only with an explicit React origin when integration starts. The Git repository's untracked baseline limits normal diff review; human review should inspect added files before any commit. The 603-row fixture is synthetic and does not prove production performance.

## 28. Frozen Phase 2 Contract

Stable for Phase 3: Java/Spring/PostgreSQL stack, isolated dev DB connection, Flyway-only schema ownership, 14 JPA entities and repositories, package direction, flat DTO boundary, `PageResponse` conventions, `ErrorResponse` shape, JWT/BCrypt/stateless authentication, `CurrentUser` identity, and 401/403 semantics. This freeze does **not** claim business services, state-transition code, department isolation or report workflows are complete.

## 29. Phase 3 Handoff

[phase_3_handoff.md](../../backend/docs/phase_3_handoff.md) states exact plan/item states, BR01–BR05, department scope gap, retention, `@Version` conflict treatment, and future `@Transactional` command boundaries. Phase 3 should extend these foundations with reviewed business services rather than moving state rules into controllers or repositories.

## 30. Final Status

**PASS.** Clean rebuild, full tests, independent package, final JAR smoke, schema/fixture parity, security/API/query checks, secret review and documentation handoff all passed. No new business feature or schema change was made.

## 31. Slide-ready Summary

- Phase 2.6 PASS: backend foundation audited and frozen for Phase 3.
- Clean DB rebuild reproduces the Phase 1 schema: **14 tables / 117 columns / 31 FKs**, V001–V006, 603 demo rows.
- **36/36** backend tests, final JAR, health and four-role login pass.
- Eight read-only business GET routes keep DTO/error/pagination contracts; JWT and director queue RBAC return correct 401/403.
- Authenticated list samples use bounded **3/4/3 SQL** including account reload; no obvious N+1.
- Phase 3 handoff defines BR01–BR05 and transaction boundaries without implementing workflow commands.
