# Version 2 — ADMIN Account Management Implementation Report

Runtime naming update: commands and source links below use the current unversioned script names. Historical JSON evidence, hashes and patches retain the names used during their original audits; see the [runtime script rename report](../runtime_script_rename_report.md) for this later change.

**Status: PASS.** Completed 2026-09-28T17:26:17.621642+07:00. Scope: account management across React → typed API client → Spring Boot → BCrypt → PostgreSQL → existing login/JWT/role/department authorization. Human UI/UX judgment remains open.

## Database implementation

**NO SCHEMA CHANGE. No V007.** V001 already supplies username uniqueness, password_hash, role_code, department_id, active and mandatory display_name. New display_name defaults to username. Existing migrations V001–V006 and all seeds are byte-identical to the pre-change baseline.

Final schema/data after isolated test cleanup: **14 business tables / 117 columns / 31 foreign keys / six successful V001–V006 migrations / 603 canonical business rows**. The existing V1 source/content freeze remains documented; V2 adds account functionality without rewriting it. Identity sequences may advance; no sequence reset is performed.

## Backend implementation

AdminAccountController is thin; AdminAccountService owns role checks, filtered paging, account creation/assignment, activation/deactivation, password replacement and self-deactivation protection. All modifying methods are transactional; locked account reads serialize concurrent mutations without adding columns. UserAccount keeps its JPA constructor and gains a named creation factory. UserAccountRepository gains filtered paging with department loading and locked reads.

Every account response uses AccountResponse: id, username, role, departmentId, departmentCode, departmentName, active. No entity/password/hash/token is returned. SecurityConfig protects both the account collection and descendants as ADMIN-only; service checks ADMIN too. Maintenance authorization matchers remain unchanged.

| Method | Endpoint | Result |
|---|---|---|
| GET | `/api/admin/accounts` | Paginated safe list; username search, role, departmentId, active filters |
| GET | `/api/admin/accounts/{id}` | Safe detail / 404 |
| POST | `/api/admin/accounts` | Validated creation, BCrypt, 201 |
| PATCH | `/api/admin/accounts/{id}` | Role/department assignment only |
| POST | `/api/admin/accounts/{id}/activate` | Activate |
| POST | `/api/admin/accounts/{id}/deactivate` | Deactivate, own-ID protection |
| POST | `/api/admin/accounts/{id}/reset-password` | Direct ADMIN-chosen password replacement |

Username is trimmed, immutable, maximum 100 characters, exact case-sensitive unique as in V1. Search is case-insensitive literal substring matching with escaped LIKE wildcards; default ordering username ascending + ID tie-breaker. Page bounds reuse PageRequests; sort accepts id/username/active. KHOA_PHONG requires an existing active department; other roles have optional existing active department association. Password must be nonblank, preserved exactly, at most 72 UTF-8 bytes (BCrypt limit); no complexity/minimum-length policy. Request toString output redacts passwords.

Structured errors use the existing ErrorResponse: 400 validation, 403 authorization, 404 ACCOUNT_NOT_FOUND, 409 USERNAME_ALREADY_EXISTS or ACCOUNT_SELF_DEACTIVATION_FORBIDDEN. A unique-constraint check also covers concurrent creates. Invalid account/department/password input does not partially mutate account state.

## Frontend implementation

ADMIN navigation **Quản lý tài khoản** and four guarded routes:

- `/admin/accounts` — list/search/combined filters, pagination, reload, empty/error/loading states.
- `/admin/accounts/new` — username, masked password, role, department, initial status.
- `/admin/accounts/:id` — detail, confirmed activate/deactivate, inline reset with matching confirmation.
- `/admin/accounts/:id/edit` — read-only username, role/department editing.

adminAccountsApi uses the central API client; account interfaces are typed. Forms clear password fields after successful creation/reset, reset cancellation/reload, and failed reset submission. No hard-delete endpoint or button exists. Navigation is hidden for other roles; direct navigation is rejected by RoleGuard and API calls independently by Spring Security. Existing V1 page implementation is unchanged; only App routes, navigation and scoped CSS are extended. Existing navy/teal/gray panels, badges, tables and feedback are reused.

## Tests and V1 regression

| Check | Final result |
|---|---|
| Frontend `npm run build --prefix frontend` | PASS |
| Frontend `npm run lint --prefix frontend` | PASS |
| Frontend `npm run test --prefix frontend` | **36 passed, 9 files**: all 20 existing V1 + 16 new |
| Backend `env -u DEBUG mvn -f backend/pom.xml test` | **103 tests, 0 failures, 0 errors, 0 skips**: all 87 existing V1 + 16 new |
| Backend packaging | PASS, existing development artifact/version retained |
| Runtime helper syntax/cold start/reuse/app stop/restart/status | PASS after two documented minimal runtime corrections |

No existing test was weakened or changed. Focused backend tests cover all endpoint role boundaries, safe DTOs, paging/search/filters, four-role creation and real login, exact password/short-password behavior, BCrypt checks, duplicate/invalid input, active status and existing JWT rejection, reset old/new login, role/department changes on existing tokens, invalid-write rollback, self-protection, missing accounts and absence of DELETE. Temporary test records are cleaned after each test using exact ID/username guards; foreign keys prevent deletion of referenced accounts.

New frontend tests cover ADMIN/non-ADMIN navigation and route guards, table/pagination/search/combined filters, empty/error/reload states, create validation/success, required KHOA department, username immutability/editing, status confirmation/cancellation, self-protection error, reset required/matching confirmation and clearing, missing-account errors.

## Real Chrome integration

**PASS: 76 live assertions**, real **Chrome 154** through CDP, two isolated browser contexts. Backend was the newly packaged application; frontend served real React pages; PostgreSQL retained actual account rows.

Four unique `SMOKE-V2-ADMIN-…` accounts (KHOA_PHONG, PHONG_VTYT, BAN_GIAM_DOC, ADMIN) were created through ADMIN UI, each with a runtime-generated simple password chosen by the audit. Each logged in through the real existing form using exactly that choice. `/api/auth/me`, role navigation and SQL metadata agreed. Password values and full hashes were never saved in evidence.

- Real list pagination; case-insensitive username search; combined role/department/status filters; empty/reset-filter states.
- Required KHOA department and duplicate username rejected in actual forms.
- Created KHOA department 1 read equipment 1 history; equipment 4 returned 403 without foreign details.
- Deactivation changed status, rejected fresh login and rejected the already-authenticated session's next protected request (401); the central client cleared its tab session and showed login.
- Activation restored login. Reset rejected the old password and accepted the new one; DB BCrypt matched only the new choice; reset fields cleared.
- Department changed to Khoa Ngoại: the same existing token returned department 8, old department history denied, equipment 38 history allowed.
- Role changed to BGD: the same token returned BGD immediately; refresh updated navigation. All four created roles had correct navigation; non-admins were blocked from account APIs/pages.
- A newly created ADMIN could access accounts, could not deactivate itself, and still received 403 for a maintenance command.
- No uncaught JavaScript error, CORS failure, React console warning/error, or credential console output was observed. Expected validation/401/403/409 responses were successful negative checks.
- Final desktop layout separates search/actions from role/department/status so filter labels fit. Account list had no document-level horizontal overflow at 1366, 1024 and 760 px. Screenshots capture list/detail without password fields; these checks do not assign a subjective UX score or claim cross-browser/WCAG certification.

Evidence: [redacted live assertions](v2_admin_account_management_evidence.json), [reproducible local driver](verify_v2_admin_accounts.py), [list screenshot](screens/v2_admin_account_list.png), [detail screenshot](screens/v2_admin_account_detail.png).

## Defects and fixes

Two genuine V1 runtime defects were documented in the design before correction. First, on a cold DB launch, PostgreSQL inherited FD 9 and retained the start/stop control lock. No helper process remained, yet repeated starts/stops were blocked. The minimal change adds `9>&-` to the setup-dev-db.sh invocation in start.sh. Only identity-verified helper app processes and this private cluster were stopped for recovery. Cold start, repeat start, app-only stop, restart and status then passed; database data remained intact. Second, after CSS HMR, Vite served the valid main.tsx entry-point URL with a timestamp query, while the helper accepted only an exact URL without a query. Frontend HTTP remained 200 but start/status reported DOWN. The minimal regex correction in runtime-common.sh accepts an optional query on the same entry-point path. Live HMR readiness, repeated start, app stop/restart and status passed afterward. No V1 business/authentication behavior required fixing.

During new-test authoring, the expected scope code was corrected to existing V1 EQUIPMENT_HISTORY_ACCESS_DENIED and label selectors accommodated form helper text. Final test runs passed without modifying any accepted V1 test.

## Cleanup and final baseline

All **4** browser smoke accounts were removed in a guarded transaction after matching exact run prefix, IDs and usernames and checking all inbound user_account FK references were zero. Canonical accounts were not used for destructive V2 tests. The product offers no deletion command; SQL cleanup exists only in the isolated verification driver. After cleanup all 14 table fingerprints, row counts, schema counts and migration history matched the canonical baseline; zero browser run-prefix accounts remain. Runtime is left available at http://localhost:5173/login.

## V1 reproducibility and changed files

The pre-change visible-file snapshot recorded 346 files. **8 existing files changed, 13 product/test files added**. Migrations, seeds, accepted V1 documentation/evidence and all existing V1 test files remain unchanged. [Source change manifest](v2_admin_source_change_manifest.json) lists exact files and original/current hashes. The repository has no initial Git commit; no commit/tag was invented.

[v1_to_v2_product.patch](v1_to_v2_product.patch) contains the reviewed product delta, including new tests and the runtime correction. Reverse and forward application were verified in a temporary copy against exact pre-change and final V2 hashes, without altering this working tree. To inspect whether it applies to an unchanged V2 source copy, run from the repository root:

```bash
patch --dry-run -R -p1 < reports/version2/admin/v1_to_v2_product.patch
```

For actual V1 reproduction, use a separate copy of the repository, stop that copy's services, reverse-apply the patch there and rebuild its backend/frontend with the existing toolchain/env configuration. The reverse patch removes the new product/test files and restores the original eight files; V1's original cold-start lock and HMR readiness defects are part of that original snapshot. Keep this V2 working directory for review. The schema and canonical seed/data baseline are shared and unchanged.

### Modified existing files

- `backend/src/main/java/vn/edu/medmaintenance/persistence/entity/UserAccount.java`
- `backend/src/main/java/vn/edu/medmaintenance/persistence/repository/UserAccountRepository.java`
- `backend/src/main/java/vn/edu/medmaintenance/security/config/SecurityConfig.java`
- `frontend/src/App.tsx`
- `frontend/src/routes/navigation.ts`
- `frontend/src/styles.css`
- `scripts/start.sh`
- `scripts/runtime-common.sh`

### Added product/test files

- `backend/src/main/java/vn/edu/medmaintenance/api/controller/AdminAccountController.java`
- `backend/src/main/java/vn/edu/medmaintenance/api/dto/request/CreateAccountRequest.java`
- `backend/src/main/java/vn/edu/medmaintenance/api/dto/request/ResetAccountPasswordRequest.java`
- `backend/src/main/java/vn/edu/medmaintenance/api/dto/request/UpdateAccountRequest.java`
- `backend/src/main/java/vn/edu/medmaintenance/api/dto/response/AccountResponse.java`
- `backend/src/main/java/vn/edu/medmaintenance/service/AdminAccountService.java`
- `backend/src/test/java/vn/edu/medmaintenance/service/AdminAccountIntegrationTest.java`
- `frontend/src/api/adminAccountsApi.ts`
- `frontend/src/pages/AdminAccountDetailPage.tsx`
- `frontend/src/pages/AdminAccountFormPage.tsx`
- `frontend/src/pages/AdminAccountListPage.tsx`
- `frontend/src/pages/AdminAccounts.test.tsx`
- `frontend/src/types/account.ts`

## Secret audit

PASS. New/changed text artifacts and evidence are checked against private local credentials without displaying them, plus JWT/full BCrypt patterns. Browser evidence records only safe metadata/booleans and network method/path/status; account POST bodies, authorization headers and login tokens are excluded. Generated temporary passwords exist only in memory. Reports do not contain raw passwords, demo passwords, JWT, JWT signing key or DB password. Password API fields and request logging remain excluded.

## Remaining limits and next action

- Password reset changes future login credentials; existing JWTs remain valid until expiry or account deactivation, preserving V1 behavior. Reactivation can make an unexpired existing token usable again.
- Backend role/department changes apply on the next request; already-open frontend role navigation updates on refresh/fresh login. Self role-demotion is allowed; self-deactivation is blocked. No broader last-admin/IAM policy was requested.
- Username matching/uniqueness remains exact case-sensitive; passwords have BCrypt's 72-byte input limit.
- No hard deletion, Repair Workflow, email/reset tokens, MFA, deployment, account audit dashboard or general Phase 6 NFR was added.

Human review of Account Management → run the [Vietnamese manual guide](v2_admin_account_management_test_guide_vi.md) with multiple roles → decide whether V2 is final → only then consider Repair Workflow or final NFR testing.
