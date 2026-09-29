# Version 2 — ADMIN Account Management Design

## Audit before implementation

V1 is frozen at its recorded source manifest in `reports/version1/integration/`. Existing files and their hashes were captured before this change; migrations/seeds and accepted V1 reports remain unchanged. Scope is account management only; no Repair, account deletion, email, MFA, deployment or general NFR work.

`database/migrations/V001__master_data.sql` already defines `user_account`: id, department_id, role_code, username, password_hash, display_name, active. Username has an exact, case-sensitive unique constraint; KHOA_PHONG requires department via a check constraint. The existing FK retains account references. V001–V006 provide all required persistence. **NO SCHEMA CHANGE; no V007.** Existing 14 tables / 117 columns / 31 FKs remain the V2 schema.

JPA UserAccount has the fields/setters but a protected constructor; add a named factory preserving the JPA constructor. Its mandatory display_name defaults to the new username, with no extra form field. Department remains the existing reference entity.

AuthenticationService uses the configured BCryptPasswordEncoder (cost 12), exact username/password login, and rejects inactive accounts. JwtAuthenticationFilter verifies the token subject, reloads the account and department, checks active and builds current authorities from DB. This supports immediate deactivation, role and department changes on the next protected request without changing JWT/auth behavior. AuthProvider restores `/api/auth/me` on refresh; role/menu changes become visible after refresh or fresh login. Password reset changes login credentials; already-issued JWTs continue until expiry/deactivation under the unchanged V1 model.

Existing role strings: PHONG_VTYT, BAN_GIAM_DOC, KHOA_PHONG, ADMIN. Demo VTYT/ADMIN use department 2, BGD has no department; the schema requires department only for KHOA_PHONG. Other roles retain optional department association; no new role-specific department restriction is invented.

## Business and validation rules

- Username: required, trim outer whitespace before validation/storage, maximum 100 characters to match LoginRequest; immutable; exact case-sensitive uniqueness matching existing DB/login. Search is case-insensitive substring matching, with LIKE wildcards escaped.
- Password: required/nonblank, preserved exactly as entered (not trimmed), BCrypt before persistence. No complexity/minimum-length requirement. Maximum 72 UTF-8 bytes is the BCrypt input limit, not a complexity rule. Request debug rendering redacts it; DTOs/errors never echo credentials.
- Role: the existing enum only; required. Invalid JSON enum values use the existing structured 400 INVALID_PARAMETER contract.
- Department: required for KHOA_PHONG; otherwise optional. Any chosen department must exist and be active. Null clears an optional department on editing.
- Active: boolean on creation; dedicated activate/deactivate commands thereafter, excluded from PATCH. Deactivate currently authenticated ADMIN's own ID returns 409 ACCOUNT_SELF_DEACTIVATION_FORBIDDEN, regardless of target status.
- Editing updates role/department only. No hard delete endpoint/control. No password/hash field in account responses.
- Transactions own create/edit/status/reset; row locks serialize writes to an account so unrelated status/password changes are preserved. No schema-level version field is added. Account edits are serialized, with the last valid edit winning rather than V1 plan/item optimistic-version semantics.
- All commands/read operations check ADMIN in service; SecurityConfig protects `/api/admin/accounts` and descendants before generic authenticated matchers. No ADMIN maintenance privilege is added.

## API

Safe account DTO: id, username, role, departmentId, departmentCode, departmentName, active. Paginated list uses existing PageResponse and PageRequests (page 0+, size 1–100; default username ascending with ID tie-breaker; allowed sort fields id/username/active).

| Method | Path | Input / behavior |
|---|---|---|
| GET | /api/admin/accounts | page, size, sort, search, role, departmentId, active; combined filters |
| GET | /api/admin/accounts/{id} | Safe account detail; 404 ACCOUNT_NOT_FOUND |
| POST | /api/admin/accounts | username, password, role, departmentId, active; 201 safe DTO |
| PATCH | /api/admin/accounts/{id} | role, departmentId; username/status/password remain unchanged |
| POST | /api/admin/accounts/{id}/activate | Set active true; safe DTO |
| POST | /api/admin/accounts/{id}/deactivate | Set active false, protect own account; safe DTO |
| POST | /api/admin/accounts/{id}/reset-password | newPassword; BCrypt replacement, safe DTO |

Reuse ErrorResponse. 400 for invalid inputs/department/password, 403 wrong role, 404 account not found, 409 duplicate username/self-deactivation. Codes include USERNAME_ALREADY_EXISTS, ACCOUNT_NOT_FOUND, DEPARTMENT_REQUIRED, INVALID_DEPARTMENT, INVALID_PASSWORD, ACCOUNT_SELF_DEACTIVATION_FORBIDDEN. Controller is thin; AdminAccountService validates and hashes; repositories handle filtered paging and locked writes.

## Frontend

ADMIN-only routes: `/admin/accounts`, `/admin/accounts/new`, `/admin/accounts/:id`, `/admin/accounts/:id/edit`, protected by the current RoleGuard. Navigation **Quản lý tài khoản** is ADMIN-only. Typed adminAccountsApi uses the current central client.

List: username search, role/department/status filters, deterministic pagination, reload/create controls; clear loading/empty/error states. Detail: username/role/department/status, edit, confirmed activation/deactivation, inline password reset with matching confirmation. Form: masked password, four-role selector, department requirement for KHOA, creation status, read-only username on editing. Password fields are cleared after successful creation/reset and on reset dismissal. Reuse current navy/teal/gray components; add only scoped account-page responsive styles.

## Verification and preservation

Focused backend/frontend tests plus the full unchanged V1 suites. Real Chrome: create unique SMOKE-V2-ADMIN accounts through UI; real fresh login, me/role/nav/scope; deactivate current session, activate, reset old/new credentials, edit role/department, wrong-role/self protection. SQL records only boolean BCrypt/plaintext checks. Runtime-generated test passwords remain in process memory, not reports/source/logs.

Capture canonical per-table fingerprints before mutations; cleanup only exact newly-created prefix/ID accounts after confirming no business references. New tests use isolated smoke records with guarded cleanup. Compare final fingerprints/schema/migrations and V1 source changes explicitly. A reverse-applicable product patch will preserve the original source form for V1 reproduction; accepted freeze artifacts are not overwritten. Reports and evidence live under `reports/version2/admin/`.

## Required V1 runtime defect discovered before live verification

The first service restart returned “Another start/stop command is running” although no helper remained. Inspection showed the private PostgreSQL postmaster and its children holding `.local-run/control.lock`; start-v1 called setup-dev-db.sh without closing FD 9. Backend/frontend launches already closed it. A cold PostgreSQL launch therefore inherited the launcher lock indefinitely. Documented before applying the minimal one-line fix: close FD 9 for that setup child. No business/auth/database change is required. Recovery stops only identity-verified helper apps and this private cluster, then cold-starts it; canonical fingerprints must match afterward. Verify cold start, repeated start, app-only stop and restart.

A second runtime readiness defect appeared after the CSS hot reload: frontend HTTP stayed 200 but start/status reported DOWN. The helper required exact `src="/src/main.tsx"`; Vite legitimately serves `src="/src/main.tsx?t=..."` after HMR. Documented before correction: accept an optional query suffix on this same entry-point path, keeping process identity checks and HTTP readiness unchanged. Verify against the live cache-busted page, repeated start and app stop/restart.
