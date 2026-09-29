# Phase 5.1 — Integration Audit

Date: 2026-09-28. Result: **PASS**. Evidence: [full report](phase_5_1_full_system_integration_report.md), [sanitized live records](phase_5_1_evidence.json), [reproducible harness](verify_phase_5_1.py). This audit reviews one completed Phase 5.1 run with 99 passing live assertions.

## Environment

Existing project-local Java 17.0.20.1, Node 24.21.0, PostgreSQL 16.15, Spring Boot 3.5.16, Vite 7.3.6 and real Chrome 154.0.8037.57. No mocked browser API or in-memory database. Passwords/signing key loaded from ignored mode-600 env files; evidence was checked for their absence.

## Startup

PASS. Existing `setup-dev-db.sh` started the project-owned private cluster on 55432. Existing packaged backend and `npm run dev --prefix frontend` started on 8080/5173. See exact commands in full report section 3. Final backend health UP; database authenticated TCP connection successful. Services remain available for review.

## Frontend ↔ Backend

PASS. Real frontend forms issued frozen command routes and reloaded server state. Cross-origin requests and preflights, including the secondary signer header, succeeded. Typed central client handled normalized errors; no CORS failure occurred.

## Backend ↔ Database

PASS. Initial, post-cleanup and post-regression schema/count/fingerprint snapshots agreed: 14 tables, 117 columns, 31 FKs, V001–V006, 603 rows. Backend API responses and SQL matched IDs, states, versions, providers, attempt numbers and timestamps.

## Authentication

PASS. `demo_vtyt`, `demo_bgd`, `demo_khoa_noi` and `demo_admin` logged in through real Chrome and `/api/auth/me` returned the expected identity/role. Full-page reloads restored identity. Invalid tab credential produced 401, readable expiry and session clearing. No raw JWT/password was retained in reports.

## Representative Read Flow

PASS. Seed equipment #1/code DEMO-EQ-001/name/department matched SQL, API and UI. Seed plan/campaign title/status/version matched SQL and both detail/history views. Provider #2 matched provider API/SQL and the proposal/approval/execution UI. Read evidence includes timestamp-instant comparisons.

## Representative Write Flow

PASS. Browser created plans #207/#208/#209 using `SMOKE-P51-20260928105623-*`. SQL confirmed generated IDs, versions, coverage and related persisted workflow evidence after commands. No database state was edited to simulate successful journeys.

## FREE Journey

PASS. Plan #207/item #292: create/submit → director approval → FREE route → attempt/progress/complete → technical PASS → KHOA handover PASS with VTYT co-signer → report DRAFT/finalization → UC12 history. UI/API/DB agreed: COMPLETED / REPORTED / FINAL, counts 1 completed and 0 repair-required. Both acceptance records and report reference survived reload.

## External Journey

PASS. Plan #208/item #293: NOT_FREE → DRAFT proposal → submitted proposal → BGD approval → ASSIGNED_EXTERNAL → execution #173 → progress. Proposed, approved, assigned and actual provider IDs were all 2; frontend provider name matched SQL. Later completion was outside the requested shortened path.

## Failure Journey

PASS. Plan #209/item #294: technical FAIL → REWORK_REQUIRED → new attempt. Executions #174/#175 and numbers 1/2 remained distinct in SQL/API/UI; old FAIL and progress persisted. No failed evidence was overwritten.

## State Consistency

PASS. Snapshots at create, route, start, technical, handover and restart agreed across layers. Final FREE plan was REPORTED v7 with FINAL report. External/rework plans remained IN_PROGRESS before cleanup. API/DB creation, start/end, progress, acceptance and finalization timestamps represented the same instants. Finalization timestamp matched its plan audit event. Approval, start and acceptance related rows appeared with their state/audit results. Existing transaction rollback regression passed.

## Version Conflict

PASS. Browser held version 5; client B saved version 6. Browser stale PUT returned 409 and conflict/reload UX. SQL plan/report fingerprint did not change and one report remained. Reload used current narrative/version; finalization returned v7. Additional rejected stale API PUT returned exact `OPTIMISTIC_LOCK_CONFLICT` with unchanged full-database snapshot.

## Restart Persistence

PASS. Frontend PID 15080 → 23451 and backend PID 14857 → 23267; same PostgreSQL cluster. External plan/item/campaign/attempt/provider/progress JSON matched before/after exactly. Reopened frontend showed the persisted progress. No business state depended on frontend memory.

## Error Integration

PASS. 401 unauthenticated/invalid session, 403 wrong role/foreign department, 404 missing equipment history and 409 stale report were verified. Shared client normalized backend ErrorResponse to readable UI. No raw Java stack trace or foreign equipment name appeared. Normal missing-DRAFT 404 probes were classified separately from failures.

## Role / Department Check

PASS. VTYT/KHOA/ADMIN were denied the BGD queue in frontend/backend; BGD was denied plan creation. ADMIN menu contained only the dashboard, with no new account management. KHOA department 1 viewed equipment #1 and was rejected for #4; its name was absent from API error/UI. Existing historical-custody tests also passed.

## Two-Signer Check

PASS. Successful handover POST retained KHOA primary Authorization and distinct temporary VTYT secondary header. Matching was recorded as booleans. Primary session stayed unchanged; only its one sessionStorage key remained, localStorage stayed empty, and signer inputs cleared/disappeared. SQL recorded both role identities and confirmation timestamps on one PASS handover.

## Console / Network

PASS after minimal fix. Zero uncaught JS errors, React warnings, CORS errors or credential logs. A settled page made no requests during a 2.5-second window. Initial favicon 404 root cause was absent favicon HTML declaration; added one `<link rel="icon" href="data:," />` line. Final report/history and restored-seed browsing had no unexpected console/network errors. Expected 401/403/404/409 probes remain documented.

## Regression

PASS after fix. Frontend build/lint and 20 tests across 8 files passed. Complete backend suite: 87 tests, zero failures/errors/skips. SHA-256 comparison found no changes to backend/frontend business source, migrations or seeds; only product edit is the favicon HTML line. No new state transitions, authorization rules, schema, features, CI/CD or deployment work.

| Backend suite | Tests | Failures/errors |
| --- | --- | --- |
| BackendFoundationIntegrationTest | 4 | 0 |
| PersistenceMappingAuditTest | 1 | 0 |
| PersistenceSmokeTest | 2 | 0 |
| ApiExceptionHandlerTest | 1 | 0 |
| ApiIntegrationTest | 9 | 0 |
| ApiQueryAuditTest | 1 | 0 |
| RepositoryIntegrationTest | 9 | 0 |
| RepositoryQueryAuditTest | 1 | 0 |
| CorsIntegrationTest | 2 | 0 |
| SecurityIntegrationTest | 8 | 0 |
| WorkflowReadIntegrationTest | 3 | 0 |
| BackendBusinessFinalIntegrationTest | 11 | 0 |
| ExecutionAcceptanceIntegrationTest | 13 | 0 |
| PlanningApprovalIntegrationTest | 8 | 0 |
| ProviderRoutingIntegrationTest | 14 | 0 |

## Cleanup

PASS. Guarded transaction deleted only the three recorded smoke plans and their dependencies after checking prefix/IDs/creator. All 14 business-table text scans contained zero SMOKE-P51 records. Post-cleanup and post-regression per-table fingerprints were identical to the original canonical snapshot: 603 rows, 14 tables, 117 columns, 31 FKs, six successful V001–V006 migrations. Identity sequences were permitted to advance.

No unresolved integration defect or cross-layer inconsistency remains. Remaining limits are local/single-browser/representative-path scope; this audit does not certify production load or deployment. **Await human review → Phase 5.2 — Integration Regression & V1 Freeze.** No Phase 5.2 or Version 2 work was started.
