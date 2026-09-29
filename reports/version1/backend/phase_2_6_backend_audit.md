# Phase 2.6 Backend Audit

**Date:** 2026-09-26. **Result:** PASS. Audit scope: Phase 2.1–2.5 foundation and the frozen Phase 1 schema/seed. Evidence came from clean rebuild, `mvn clean test`, `mvn clean package`, packaged-JAR HTTP smoke, PostgreSQL metadata/content comparison, Maven dependency tree, source review, startup logs and Git ignore checks.

## A. Environment

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| Toolchain | Java 17, Maven 3.9.11, PostgreSQL 16 | Temurin 17.0.20.1, Maven 3.9.11, PostgreSQL 16.15 | PASS |
| Local DB | Isolated backend dev cluster | `medical_maintenance_backend_dev` at `127.0.0.1:55432` | PASS |
| Local secrets | Ignored, private | Both env files under `.local-postgres/`, mode 600 | PASS |

## B. Build

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| Clean compile/test | No skipped or failing tests | `mvn clean test`: 36 run, 0 failures/errors/skips | PASS |
| Clean package | Runnable JAR | `mvn clean package`: success, 60 MB JAR | PASS |
| Packaging | Six source migration resources | Six JAR SQL resources byte-identical to source | PASS |
| Build output | Ignored | `backend/target/` matched `.gitignore` | PASS |

## C. Database

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| Schema inventory | 14 tables / 117 columns / 31 FKs | 14 / 117 / 31 | PASS |
| Demo data | 603 rows, exact per-table distribution | 8, 15, 40, 7, 35, 8, 52, 19, 15, 30, 90, 40, 4, 240 = 603 | PASS |
| Phase 1 parity | Same business schema and fixture | Normalized schema dumps byte-identical; all 14 tables' canonical row JSON match after excluding `password_hash` | PASS |
| Credential exception | Only four demo hashes changed | `demo_vtyt`, `demo_bgd`, `demo_khoa_noi`, `demo_admin` only | PASS |

## D. Flyway

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| Versions | V001–V006 only | Six successful SQL rows, no V007 | PASS |
| Repeat startup | Validate, no replay | “Successfully validated 6 migrations”; “No migration necessary” | PASS |
| Ownership | Flyway creates, Hibernate validates | `ddl-auto=validate`, `open-in-view=false`; no DDL at Hibernate startup | PASS |
| Source integrity | Frozen files unchanged | Before/after migration and seed SHA-256 lists identical; JAR migration bytes match | PASS |

## E. JPA

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| Mapping | 14/14 entities, 117/117 fields, 31/31 FKs | [mapping audit](phase_2_2_mapping_audit.md) PASS | PASS |
| State vocabularies | 11 exact constrained enums | 11/11 PASS | PASS |
| Optimistic locks | Plan and item only | Exactly two `@Version` fields | PASS |
| Flyway metadata | No JPA entity | No entity for `flyway_schema_history` | PASS |

## F. Repository

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| Coverage | 14 query repositories | 14, including UC12 two-campaign path | PASS |
| Retention surface | No custom destructive bulk command | No `@Modifying`, custom delete or bulk update method | PASS |
| List queries | Bounded SQL | Phase 2.3 samples: 2/2/2 SQL; [query audit](phase_2_3_query_audit.md) | PASS |

## G. API

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| Existing contract | Eight read-only business GET routes | Eight, plus login and `/me`; no workflow write | PASS |
| DTO boundary | No JPA entity response | Explicit response records; auth now uses `AuthenticatedUserResponse` | PASS |
| Input/output | Page 0/size 20, max 100, allowlisted sort, safe errors | Phase 2.4 tests and [API audit](phase_2_4_api_audit.md) PASS | PASS |
| Error status | 400/401/403/404/405/409/500 | HTTP and direct-handler cases PASS; no exception/SQL leak | PASS |

## H. Security

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| Public | Login and health | Both 200 without prior token | PASS |
| Protected | `/api/**`, director queue | Anonymous equipment 401; BGĐ queue 200, VTYT/Khoa/Admin 403 | PASS |
| JWT | Signed, expiring, minimal claims | JJWT 0.13.0; 3600 s; invalid/expired token 401; user reloaded | PASS |
| Four roles | All synthetic accounts authenticate | Four login and `/me` HTTP paths 200 with exact roles | PASS |
| CORS | No wildcard/open origin | Browser preflight from `localhost:5173` got 401 and no allow-origin header | PASS |

## I. Secrets

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| Externalization | No hardcoded DB/JWT credential | Env placeholders only; actual values absent from backend source, reports and final DEBUG logs | PASS |
| Logging | No token/password/default Spring user | Final DEBUG-level startup/test logs had no generated password, raw JWT or actual secret; older leaky temporary audit logs were removed | PASS |
| Ignore/permission | Local env and target ignored | Git ignored both env files and target JAR; security env mode 600 | PASS |

The first DEBUG-level JAR smoke exposed demo passwords and issued JWTs through generated record `toString()` output in Spring MVC diagnostic logs. `LoginRequest` and `LoginResponse` now redact those fields; a new test checks their string rendering, and a four-account DEBUG-level JAR smoke verified **zero raw passwords and zero issued tokens** in the final log. The older temporary logs containing credentials were deleted. This was a confirmed foundation defect, not a hypothetical warning.

## J. Architecture

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| Dependency direction | Persistence independent of API; security surrounds HTTP | Zero persistence→API imports; controllers have no SQL/JWT parser; mappers have no DB calls | PASS |
| Read layering | Controller→repository/mapper→DTO | Four small read controllers; no business command orchestration | PASS |
| Auth layering | Principal separate from wire DTO | `AuthenticatedUserResponse` now maps from internal principal | PASS |
| Phase 3 boundary | Business service absent | Documented transactional insertion point; no UC command added | PASS |

## K. Tests

| Layer | Tests | Main coverage |
| --- | ---: | --- |
| Foundation (2.1) | 4 | DB, schema, context, health |
| Persistence (2.2) | 3 | Mapping/enum/FK audit, lazy relations, `@Version` |
| Repository (2.3) | 10 | Query paths, UC12, SQL counts |
| API (2.4) | 11 | DTO, page/sort/filter, errors, JSON leakage, HTTP SQL |
| Security (2.5 + 2.6) | 8 | Four roles, JWT, BCrypt, 401/403, active/role reload |
| **Total** | **36** | **0 failures, 0 errors, 0 skips** |

No `@Disabled` tests. Seed IDs are generally looked up by stable codes rather than assumed raw values; write tests roll back or restore temporary account changes. The 409/500 generic handlers are tested directly because no Phase 2 write route can trigger them over HTTP.

## L. Documentation

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| New developer path | DB → migrate → seed → credentials → test/JAR | [README](../../backend/README.md) rewritten in executable order; Bash blocks pass `bash -n` and the documented login command issued a token | PASS |
| Layer guide accuracy | Current foundation status | Stale “no authentication/repositories” wording corrected | PASS |
| Handoff | Explicit Phase 3 assumptions/boundaries | [Phase 3 handoff](../../backend/docs/phase_3_handoff.md) and [architecture](../../backend/docs/backend-architecture.md) created | PASS |
| Demo tool dependency | Clear failure if Python BCrypt missing | Bootstrap checks `import bcrypt` and documents `python3-bcrypt` | PASS |

## M. Git Hygiene

| Check | Expected | Actual | Result |
| --- | --- | --- | --- |
| No commit/push | Workspace only | None made | PASS |
| Migration/seed/PDF | No source modification | SQL before/after hashes match; both source PDF SHA-256 values match the Phase 1.2 report; schema/fixture parity verified | PASS |
| Runtime noise | Ignored | `.local-postgres/` and `backend/target/` ignored | PASS |
| Diff visibility | Reviewable changes | Repository files were already untracked at baseline, so `git diff --stat`/`git diff` are empty; file-level review and checksums used | LIMITATION |

## Accepted limits

These checks prove behavior on the 603-row synthetic fixture, not production throughput. Department business isolation, workflow commands, CORS for a future frontend and refresh-token handling are outside Phase 2. The pending director queue rule does not authorize a director decision.
