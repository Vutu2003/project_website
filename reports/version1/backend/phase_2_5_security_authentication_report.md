# Phase 2.5 — Authentication, Security & RBAC Foundation

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-25  
**Status:** **PASS**

## 1. Objective

Protect the existing read-only REST API using existing `user_account` rows, a login endpoint, short-lived JWT Bearer tokens and one high-level role boundary. The frozen schema and Phase 1.3 workflow fixture remain intact.

## 2. Starting Point

Phases 2.1–2.3 supplied Spring Boot, PostgreSQL/Flyway, 14 mapped entities and repositories. Phase 2.4 supplied eight GET endpoints and safe JSON errors, but no authentication. The 15 seeded account hashes were unknown placeholders. No business service or frontend existed.

## 3. Authentication vs Authorization

**Authentication** asks who made the request; it lets the server trust a verified user ID. Here, login checks a BCrypt hash and later requests validate a signed token. **Authorization** asks whether that user may use a route; here, all business reads require a login and one approval queue has a role restriction. Detailed UC rules are deferred.

## 4. What is Spring Security?

Spring Security is the Java framework that places security checks before controllers. Its `SecurityFilterChain` is the ordered request pipeline. Here it accepts public login/health, runs the JWT filter and enforces authenticated or role-based access. This gives one consistent boundary instead of checks scattered across four controllers.

## 5. Security Architecture

```text
Client → Authorization: Bearer <JWT> → SecurityFilterChain
       → JwtAuthenticationFilter → UserAccount lookup → SecurityContext
       → role authorization → controller → existing DTO
```

`SecurityContext` holds the `AuthenticatedUser` principal for the current request. The `CurrentUser` helper exposes ID, username, role and department ID to later services without parsing JWT there.

## 6. Why JWT?

A JWT is a signed, expiring token that the separate future React client can send in an HTTP Authorization header. The server can verify it without storing a session. [JJWT 0.13.0](https://github.com/jwtk/jjwt/releases/tag/0.13.0) is the one JWT library, chosen for its small Java API and HMAC support; its API, implementation and Jackson modules share that exact version. No OAuth2 server was added.

## 7. Stateless Authentication

The filter validates every Bearer request and reloads the account from PostgreSQL. `SessionCreationPolicy.STATELESS` means no server HTTP login session. A disabled account or changed role takes effect on its next request, even while an old token has time left. One account query per request is an accepted foundation cost.

## 8. Password Security / BCrypt

Password hashing stores a one-way digest rather than plaintext. BCrypt adds a random salt and work factor; Spring's `BCryptPasswordEncoder(12)` verifies a raw login password against the stored hash with `matches`. The dev bootstrap creates `$2b$12$` hashes. Tests show correct passwords match and wrong passwords do not; no hash equality comparison is used.

## 9. Existing User Model

The frozen `user_account` has username, `password_hash`, `role_code`, optional `department_id` and `active`. No second user/role table was created. The role enum is exactly `PHONG_VTYT`, `BAN_GIAM_DOC`, `KHOA_PHONG`, `ADMIN`; the principal omits the hash and display name.

## 10. Demo Login Strategy

`backend/scripts/setup-demo-login.sh --env-only` creates a local ignored mode-600 file with a random signing key and four random passwords before first app startup. After migrations and seed, the normal script verifies the isolated DB identity and exactly four active synthetic accounts, then updates only their BCrypt hashes in one transaction. This is outside Flyway; re-running it keeps local passwords and replaces salts. The [demo account guide](../../backend/docs/demo-accounts.md) lists usernames without publishing credentials.

## 11. Login API

`POST /api/auth/login` takes nonblank `username` and `password`. A valid demo account returns HTTP 200 with `accessToken`, `tokenType: Bearer`, `expiresIn: 3600`, and a minimal user DTO. Wrong password, unknown username and inactive user all return the same safe 401 `INVALID_CREDENTIALS`. Login does not log the request body.

## 12. JWT Structure

The signed token contains `sub` = user ID, role at issue time, optional department ID, issued-at and expiration. It has no hash, full name, equipment data or entity object. Tests decode claims and confirm their exact set. Authorization always uses the freshly loaded DB role rather than trusting an old role claim.

## 13. Token Expiration

Access tokens last **3600 seconds (one hour)**. The environment property can select 60–3600 seconds; invalid bounds fail startup. A signed token with a past expiry returned 401 in HTTP tests. There is no refresh token in this phase.

## 14. Current User Endpoint

`GET /api/auth/me` requires a Bearer token and returns `id`, `username`, `role`, `departmentId`. HTTP tests for all four roles confirm it reflects `SecurityContext` and matches current seeded account IDs and departments.

## 15. Four Roles

`PHONG_VTYT` coordinates maintenance; `BAN_GIAM_DOC` is the director actor; `KHOA_PHONG` represents a clinical department; `ADMIN` is technical administration. All four synthetic accounts authenticate. Database values are mapped to `ROLE_<value>` Spring authorities without changing the enum or schema.

## 16. RBAC Foundation

RBAC means role-based access control. The existing pending director approval queue allows `BAN_GIAM_DOC`; `PHONG_VTYT`, `KHOA_PHONG` and `ADMIN` receive 403. The source labels this a director queue for UC04/UC07, so this route is the narrow demonstrable role boundary. No plan/vendor decision endpoint or UC-specific rule was introduced.

## 17. Department Context

A Khoa phòng account's seeded department ID is carried in the token and freshly loaded principal; `/me` confirms it. This gives future services a trusted scope value. Equipment and plan reads are **not** yet filtered by department, so this phase makes no isolation claim.

## 18. Protected Endpoints

Login and Actuator health are public. Every `/api/**` read requires authentication, with the approval queue requiring the director role. Health alone is exposed by Actuator. Existing response DTO and pagination/filter/sort semantics remain the same for authenticated requests.

## 19. 401 vs 403

HTTP **401** means the request lacks usable authentication: absent token, invalid/expired token or failed login. HTTP **403** means a valid account is known but lacks permission for the selected route. Tests exercised both through the real filter chain.

## 20. Security Error Responses

Security failures reuse Phase 2.4 `ErrorResponse`: timestamp, status, error, code, message, path and empty field-errors list. Codes are `AUTHENTICATION_REQUIRED`, `INVALID_TOKEN`, `INVALID_CREDENTIALS`, `ACCESS_DENIED`. Bodies contain no parser stack, SQL, hash, token or secret; there is no default HTML security page.

## 21. Secret Management

`JWT_SECRET` must be Base64 of at least 32 random bytes; the local helper generates 48 random bytes. The app fails clearly if absent or weak. The real key and passwords live only in `.local-postgres/backend-security.env`, ignored by Git and mode 600. `backend/.env.example` has placeholders only. No signing key or password is embedded in Java, YAML or SQL migrations.

## 22. CORS

CORS has not been opened. Browsers from another origin need an explicit, reviewed development origin later; there is no `*` origin or wildcard credential setting. Direct API clients and same-origin requests work now.

## 23. CSRF

CSRF protection targets requests where browsers automatically attach authentication cookies. This API is stateless and sends credentials explicitly in `Authorization: Bearer`; it does not rely on browser auth cookies, so CSRF is disabled for this architecture. Form login and HTTP Basic are disabled as well.

## 24. Security Tests

Seven new HTTP/PostgreSQL integration tests cover four-role login, `/me`, BCrypt, exact JWT claims, malformed/tampered/expired tokens, no token, 401/403 body, protected reads, active flag and current role reload. All **7/7** passed. Temporary account disable/role changes were restored in `finally` blocks.

## 25. Four-role Login Validation

Packaged-JAR smoke checks and automated tests logged in `demo_vtyt`, `demo_bgd`, `demo_khoa_noi`, `demo_admin`: all returned 200, then `/me` returned 200 and the exact frozen role. The director queue call returned 200; VTYT, Khoa phòng and Admin returned 403.

## 26. Phase 2.4 Regression

The nine existing API HTTP tests, one query audit and one error-handler test passed after using a valid Bearer token. Pagination, filtering, sorting, DTO leakage and safe errors retain their assertions. Representative authenticated list requests use **3, 4, 3** SQL statements for equipment, plan items and pending approvals respectively, each exactly one more than Phase 2.4 due to account reload.

## 27. Full Backend Regression

A clean `mvn clean test package` after reset, migrate, seed and credential bootstrap passed **35 tests, 0 failures, 0 errors**. This includes all Phase 2.1–2.3 foundation/persistence/repository tests. The packaged JAR started and returned health `UP`; live HTTP checked login, `/me`, equipment and role boundaries.

## 28. Database Regression

Final PostgreSQL metadata: **14 business tables, 117 columns, 31 FKs, 6 successful V001–V006 migrations, 603 demo rows**. No migration, seed SQL, entity mapping or workflow row was changed. Only four intentional `user_account.password_hash` values differ from the original placeholder seed.

## 29. Security Audit

The [security audit](phase_2_5_security_audit.md) records authentication, authorization, JWT, credential and endpoint exposure matrices. Log and source searches found no generated Spring password after correction, no logged Bearer token, no actual secret, and no plaintext demo password in tracked project files.

## 30. Problems Found

Adding Spring Security initially enabled its unused autogenerated in-memory user and printed a generated password during test startup. A filter code review also found that its JWT exception handler enclosed downstream controller execution, which could have misreported a controller `IllegalArgumentException` as an invalid token.

## 31. Fixes Applied

The unused `UserDetailsServiceAutoConfiguration` was excluded, leaving the explicit database-backed login/filter path and removing the generated-password log. The JWT `try/catch` now covers token verification/account mapping only; downstream controller execution occurs afterward. Clean tests and packaged-JAR checks passed following both fixes.

## 32. What Has NOT Been Implemented

No detailed UC authorization, maintenance business state rules, department isolation over all queries, refresh-token flow, frontend, OAuth2/SSO or workflow mutation was added. The approval queue role check authorizes a read, never a director decision.

## 33. Phase 2.6 Handoff

Audit integration and freeze this backend foundation before business services. Use `CurrentUser` for trusted actor/department context in later services, enforce department and UC rules next to those services, and review an exact CORS origin when a browser client is integrated.

## 34. Final Status

**PASS.** Spring Security, BCrypt, external-secret JWT, one-hour expiry, stateless filter, four-role login, `/me`, authenticated `/api/**`, the narrow queue RBAC boundary, safe 401/403 responses, 35/35 tests and unchanged frozen schema are verified.

## 35. Slide-ready Summary

- Phase 2.5 PASS: Spring Security + BCrypt + JJWT Bearer, stateless, one-hour token.
- Four synthetic role accounts authenticate; `/me` supplies trusted ID, role and department context.
- Existing `/api/**` reads now require JWT; pending approvals have a narrow BGĐ role boundary.
- Invalid login/token and forbidden role return safe JSON 401/403.
- Clean rebuild and **35/35** backend tests pass; database stays **14 / 117 / 31**, V001–V006 and 603 demo rows.
