# Phase 2.5 Security Guide

## Architecture

`POST /api/auth/login` checks an existing `user_account` with Spring Security's BCrypt encoder, then issues an HMAC-signed JWT. Each protected request sends `Authorization: Bearer <token>`. `JwtAuthenticationFilter` verifies signature and expiry, reloads the current user and active flag from PostgreSQL, and places a minimal `AuthenticatedUser` in `SecurityContext`. The controller receives only DTOs. `CurrentUser.get()` is the future service-facing context helper.

Stored `role_code` values remain the frozen `PHONG_VTYT`, `BAN_GIAM_DOC`, `KHOA_PHONG`, `ADMIN`. The filter maps each to a Spring authority named `ROLE_<role_code>`. Authorization uses the **current database role**; the signed role claim describes issuance time and does not override a later role change. An inactive account loses access immediately even with an unexpired token. This adds one bounded user lookup per authenticated request.

[JJWT 0.13.0](https://github.com/jwtk/jjwt/releases/tag/0.13.0) is the sole JWT library (API, implementation and Jackson modules). Tokens contain only `sub` (user ID), `role`, optional `departmentId`, `iat` and `exp`. No password hash, display name or entity graph is included. The signing key comes from Base64 `JWT_SECRET` containing at least 32 random bytes; startup fails without it. `JWT_EXPIRATION_SECONDS` defaults to **3600** and is restricted to 60–3600. There is no refresh token or session table.

## Endpoint matrix

| Endpoint group | Anonymous | Authenticated | Role-specific |
| --- | --- | --- | --- |
| `POST /api/auth/login` | Allowed | Allowed | None |
| `GET /actuator/health` | Allowed | Allowed | None |
| `GET /api/auth/me` | 401 | Allowed | None |
| `GET /api/departments`, `/api/equipment`, `/api/plans`, `/api/providers` | 401 | Allowed | None |
| `GET /api/approvals/pending` | 401 | Allowed | `BAN_GIAM_DOC` only |
| Other `/api/**` | 401 | Authenticated routing/errors | No business rules added |

The approval queue restriction follows the source's pending director queue for UC04/UC07; it does **not** authorize a decision. Detailed business authorization, department-scoped data reads, and UC rules belong to Phase 3. `KHOA_PHONG` has its seeded `departmentId` in the principal; this phase does not automatically filter equipment or history to that department.

## Errors and request security

Missing credentials return HTTP 401 `AUTHENTICATION_REQUIRED`; malformed, tampered, expired or disabled-account tokens return 401 `INVALID_TOKEN`; failed login returns 401 `INVALID_CREDENTIALS` for both unknown users and incorrect passwords. A valid token with insufficient role returns 403 `ACCESS_DENIED`. All use the Phase 2.4 `ErrorResponse` JSON fields and omit parser details. Login and security errors set no-store on token responses or filter errors. The login request/response records redact password and access token in `toString()`, including when Spring MVC DEBUG logging formats them. No raw token, password, hash or secret is logged by application code.

The API is stateless (`SessionCreationPolicy.STATELESS`). CSRF is disabled because this design sends credentials explicitly in the Authorization header and does not rely on browser cookies for authentication. Form login and HTTP Basic are disabled. Phase 4.1 permits exactly `${FRONTEND_ORIGIN:http://localhost:5173}` for `/api/**` browser requests. It permits Authorization, Content-Type and the handover co-signer header, without wildcard origin or credentialed CORS. Spring Security remains authoritative for route access. Only Actuator health is exposed among management endpoints; `/api/auth/login` remains public.

## Development setup

Run from the repository root:

```bash
source scripts/use-toolchain.sh
./backend/scripts/setup-dev-db.sh
./backend/scripts/setup-demo-login.sh --env-only
source .local-postgres/backend-dev.env
source .local-postgres/backend-security.env
```

Start the backend once to apply Flyway, stop it, run `./backend/scripts/seed-dev-db.sh`, then run `./backend/scripts/setup-demo-login.sh` to replace only four synthetic placeholder hashes. Re-run bootstrap after any DB reset. The local security env file is ignored by Git and mode 600. Its generated passwords and signing key stay on this machine. For account names and login examples see [demo-accounts.md](demo-accounts.md) and [README](../README.md).
