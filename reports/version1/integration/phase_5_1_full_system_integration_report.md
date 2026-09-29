# Phase 5.1 — Full System Integration

Date: 2026-09-28 (Asia/Ho_Chi_Minh). Status: **PASS**. Repository: `/home/vutu0809/Desktop/LTNC`.

## 1. Objective

Validate the frozen Version 1 frontend ↔ backend ↔ PostgreSQL as one real local system. This run covers startup, authentication, browser commands, SQL/API/UI comparisons, persistence, failures, authorization, regression and cleanup. It does not start Phase 5.2 or Version 2.

Evidence: [sanitized check and network records](phase_5_1_evidence.json), [99-check audit harness](verify_phase_5_1.py), and [integration audit](phase_5_1_integration_audit.md). Browser commands use real Chrome DOM controls and the existing application client; no HTTP mocking or direct SQL success simulation was used for the three journeys.

## 2. Environment

| Component | Verified environment |
| --- | --- |
| PostgreSQL | 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1) |
| Java | openjdk version "17.0.20.1" 2026-08-18 |
| Node | v24.21.0 |
| Browser | Google Chrome 154.0.8037.57, real headless Chrome through CDP |
| Backend | Existing Spring Boot 3.5.16 packaged application; development profile |
| Frontend | Existing React/TypeScript app; Vite 7.3.6 |
| Frontend URL | http://localhost:5173/login |
| Backend | http://localhost:8080; health `/actuator/health` |
| Database | `127.0.0.1:55432`, `medical_maintenance_backend_dev` |

Passwords and signing key came from the ignored, mode-600 `.local-postgres/backend-security.env`; DB credentials came from `.local-postgres/backend-dev.env`. No credential values are present in this report or its evidence.

## 3. Startup Sequence

Run from the repository root. These are the working local commands used in this audit:

```bash
source scripts/use-toolchain.sh
./backend/scripts/setup-dev-db.sh
source .local-postgres/backend-dev.env
source .local-postgres/backend-security.env
```

In the backend terminal, with the same toolchain and env files sourced:

```bash
env -u DEBUG java -jar backend/target/medical-equipment-maintenance-backend-0.1.0-SNAPSHOT.jar
```

In the frontend terminal:

```bash
source scripts/use-toolchain.sh
npm run dev --prefix frontend
```

Then check:

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS -o /dev/null -w '%{http_code}\n' http://localhost:5173/login
```

The existing packaged JAR and seeded database were reused. No reset, reseed, schema creation or new migration was required. Persistent foreground sessions were used; short-lived shell background jobs did not remain running in this tool environment.

The audit uses installed `websocket-client`, `psql`, and an isolated Chrome profile. Start its browser with:

```bash
google-chrome --headless=new --disable-gpu --no-first-run \
  --no-default-browser-check --remote-debugging-port=9222 \
  --remote-allow-origins=http://localhost:9222 \
  --user-data-dir=/tmp/ltnc-p51-chrome about:blank
```

The recorded harness stages are `baseline`, `free`, `external`, `rework`, `boundaries`, `restart`, `contracts`, `cleanup`, `final`. Restart frontend and backend between `boundaries` and `restart`; run backend regression after `cleanup` and before `final`. Each stage uses `python3 reports/version1/integration/verify_phase_5_1.py STAGE` with both ignored env files sourced. Evidence belongs to this completed run; a fresh run should use a separate evidence file, preserving the reviewed run.

## 4. Layer Connectivity

PASS. PostgreSQL accepted authenticated TCP connections. Backend health returned `{"status":"UP"}`; real business reads and writes reached PostgreSQL. Frontend and login rendered at port 5173, and browser API requests reached port 8080. Browser preflights succeeded, including the handover secondary header. No CORS errors occurred.

## 5. Authentication Integration

All four demo users logged in through the real browser, received a JWT, and resolved identity through `/api/auth/me`.

| Account | API role | Frontend navigation |
| --- | --- | --- |
| `demo_vtyt` | PHONG_VTYT | Plans, execution, reports, equipment/history |
| `demo_bgd` | BAN_GIAM_DOC | Approval queue, reports, equipment/history |
| `demo_khoa_noi` | KHOA_PHONG, department 1 | Scoped equipment/history and handover |
| `demo_admin` | ADMIN | Dashboard only |

Page reloads restored authenticated identity from `/api/auth/me`. An intentionally invalid primary credential returned 401, cleared the session, and displayed the readable login-expiry message.

## 6. Database → Backend → Frontend Read Validation

Seeded `DEMO-EQ-001`, equipment ID 1, “Máy theo dõi bệnh nhân”, department 1 matched SQL, equipment API and the browser. A seeded campaign's plan ID/title/status and plan/item versions matched SQL, API, plan detail and history. Provider ID 2, “Đơn vị bảo trì ngoài demo”, matched the provider API, SQL, browser proposal/approval and actual execution display. IDs and names were compared programmatically; visual inspection was supplemented by SQL/API assertions.

## 7. Frontend → Backend → Database Write Validation

Three single-item plans were created from the actual frontend with prefix `SMOKE-P51-20260928105623`. Browser responses supplied generated plan/item IDs and server versions. SQL then confirmed persisted plan/title/status/version, item/equipment/coverage/provider, and related workflow evidence. No state or version was fabricated in the database.

## 8. FREE Happy Path

Plan #207, item #292, equipment #1, FREE coverage #1, provider #1:

VTYT created/submitted → BGD approved → VTYT selected FREE coverage → started attempt 1 → appended progress → completed work → technical PASS → KHOA handover PASS with temporary VTYT co-signer → VTYT created DRAFT → resolved a deliberate concurrent edit conflict → finalized report → opened UC12 history.

Final UI/API/DB: **item COMPLETED, plan REPORTED, report FINAL**. Report #36 returned completed count 1 and repair-required count 0. History retained one attempt, one progress log, both PASS acceptances and the FINAL report reference.

## 9. External Provider Path

Plan #208, item #293, equipment #2, NOT_FREE coverage #2:

Browser create/submit/approve → NOT_FREE route → vendor DRAFT → proposal submit → BGD approval → ASSIGNED_EXTERNAL → start execution #173 → append progress.

Proposal, director review, item assignment, execution, SQL and frontend display all identified **provider #2**. The incomplete IN_MAINTENANCE workflow was intentionally used for restart verification; later external completion was outside this representative shorter journey.

## 10. Failure / Rework Path

Plan #209, item #294, FREE equipment #1:

Attempt 1 started → progress → work completed → technical FAIL → REWORK_REQUIRED → distinct attempt 2 started.

Executions #174 and #175 remained separate, with attempt numbers 1 and 2. Browser and history API showed both; SQL retained the old ended attempt and FAIL acceptance beside the new open attempt. Prior evidence was unchanged.

## 11. UI / API / DB State Consistency

The following states were captured before guarded smoke cleanup:

| Plan / item | Plan state/version | Item state/version | Provider ID | Attempts | Report |
| --- | --- | --- | --- | --- | --- |
| 207 / 292 (free) | REPORTED / v7 | COMPLETED / v5 | 1 | 1 | FINAL |
| 208 / 293 (external) | IN_PROGRESS / v3 | IN_MAINTENANCE / v4 | 2 | 1 | No report |
| 209 / 294 (rework) | IN_PROGRESS / v3 | IN_MAINTENANCE / v5 | 1 | 2 | No report |

Creation, routing, execution start, technical assessment, handover and restart each had SQL/API/UI comparisons. APIs and UI used current server versions. Provider and attempt identities agreed. Plan creation, execution start/end, progress, acceptance and report-finalization timestamps were compared as instants, permitting timezone formatting differences.

## 12. Version / Optimistic Conflict

Browser client A loaded report plan version 5. API client B changed its narrative using version 5; server returned version 6. Browser A's old PUT returned **409 OPTIMISTIC_LOCK_CONFLICT** and displayed “Xung đột phiên bản” with a reload action. Reload retrieved the changed narrative/current version; finalization used it and returned plan version 7.

A SQL fingerprint of the plan/report pair remained unchanged across the rejected browser command, with exactly one report retained. An additional stale API PUT verified the exact error code and unchanged full-database fingerprints. There was no automatic retry, duplicate report or partial overwrite.

## 13. Transaction Consistency

After successful approval, DECIDED request, APPROVE action and APPROVED audit state were present together. Execution start retained the generated attempt/provider and IN_MAINTENANCE audit entry. Technical acceptance retained its result and corresponding state. FINAL report, REPORTED plan and FINALIZE_REPORT history appeared together; report and audit shared the same persisted timestamp.

The existing full backend suite additionally passed its rollback/fault-injection coverage, including failed report finalization. No transaction boundaries were changed.

## 14. Restart Persistence

Before restart, external plan #208 held IN_PROGRESS/IN_MAINTENANCE with execution #173 and a `SMOKE-P51-20260928105623-RESTART-PERSISTED` progress entry.

Frontend process changed from PID 15080 to 23451; backend changed from 14857 to 23267. The same PostgreSQL cluster stayed running. Reopened workflow data, including plan/item versions and the complete campaign/attempt/progress response, matched the pre-restart snapshot exactly. Business state was reconstructed from PostgreSQL.

## 15. Error Integration

| Error | Real verification | UI behavior |
| --- | --- | --- |
| 401 | Anonymous equipment API; invalid-session `/api/auth/me` | Session cleared; readable expiry/login message |
| 403 | Wrong-role protected endpoint; foreign-department history | Role guard or normalized permission message; foreign identity hidden |
| 404 | Missing equipment history #999999999 | Normalized missing-data message |
| 409 | Stale report PUT | Version conflict and explicit reload |

Backend ErrorResponse reached the shared frontend client. No raw Java stack trace was displayed. Expected initial report/vendor-DRAFT absence also returned 404 during normal probing.

## 16. Role / Department Integration

All four roles passed representative frontend guard/backend authorization comparisons. VTYT, KHOA and ADMIN were denied the BGD approval queue; BGD was denied plan creation. ADMIN gained no account-management or invented business action.

KHOA department 1 could see equipment #1 and its permitted history. Foreign equipment #4 returned 403; “Máy sốc điện” was absent from API error and UI output. Existing historical-custody regression also passed; this run did not manufacture department transfers.

## 17. Two-Signer Regression

The real KHOA handover POST used primary `Authorization` matching the KHOA session and a distinct temporary VTYT token in `X-VTYT-Authorization`; preflight succeeded. The primary session was unchanged, sessionStorage contained only its one primary credential, localStorage stayed empty, and signer fields disappeared/cleared after completion.

SQL retained KHOA and VTYT signer identities and confirmation timestamps on the same PASS handover record. Header values and tokens were never saved in evidence.

## 18. Browser Console / Network

Zero uncaught JavaScript errors, React warnings, CORS errors and credential logs were observed. A settled history page issued no new API requests over a 2.5-second observation window; no infinite fetch loop was seen.

Initial run logged a missing `/favicon.ico` 404. The one-line fix removed that request; final real report/history and post-cleanup canonical browsing had zero unexpected console/network errors. Expected negative 401/403/404/409 and normal missing-DRAFT probes are retained and classified in evidence. Request records store methods, paths, versions and header-presence/matching booleans, never raw credentials.

## 19. Regression Tests

After the favicon fix:

```bash
npm run build --prefix frontend
npm run lint --prefix frontend
npm run test --prefix frontend
```

All passed: **20 frontend tests across 8 files**.

After guarded smoke cleanup, with both local env files sourced:

```bash
env -u DEBUG mvn -f backend/pom.xml test
```

**87 backend tests passed**, zero failures, errors or skips, in 34.370 seconds. This is the complete existing suite, including business, authorization, CORS, reads, mapping and repository coverage.

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

## 20. Problems Found

One minor frontend integration defect: HTML supplied no favicon, so Chrome requested nonexistent `/favicon.ico` and added a 404 to the console/network evidence. Core journeys and data consistency were unaffected.

The tool sandbox initially failed to initialize with `mountinfo path is not absolute`; authorized local commands were executed through the available reviewed escalation path. Short-lived background launch attempts did not stay running; persistent sessions successfully ran the stack. These were audit-environment issues, not product business defects.

## 21. Fixes Applied

Added `<link rel="icon" href="data:," />` to `frontend/index.html`. This prevents the nonexistent favicon request. Final browser checks and frontend build/lint/20 tests passed after the change.

Backend/frontend business source, role rules, state machines, optimistic locking, transaction boundaries, migrations and seed files were unchanged; SHA-256 snapshots verified the frozen source directories. The only product-file edit is the one-line HTML declaration. New files contain Phase 5.1 reports and the integration audit harness/evidence.

## 22. Database Cleanup

A guarded SQL transaction matched exactly the three recorded Phase 5.1 plans, their unique run prefix and `demo_vtyt` ownership, then deleted only their report, acceptance, progress, execution, approval, state-history and item dependencies. No canonical row was deleted or rewritten.

Post-cleanup and post-backend-regression checks both matched every canonical per-table row fingerprint, counts, schema and migration history. All 14 business tables contained **zero SMOKE-P51 text records**.

| Business table | Restored rows |
| --- | --- |
| department | 8 |
| service_provider | 7 |
| user_account | 15 |
| equipment | 40 |
| maintenance_coverage | 35 |
| maintenance_plan | 8 |
| maintenance_plan_item | 52 |
| approval_request | 19 |
| approval_action | 15 |
| maintenance_execution | 30 |
| maintenance_progress_log | 90 |
| acceptance_record | 40 |
| maintenance_report | 4 |
| status_history | 240 |
| **Total** | **603** |

Final baseline: **14 business tables, 117 columns, 31 FKs, successful V001–V006, 603 rows**. Identity sequences can advance after smoke/regression inserts; they were not reset, and no business row depended on sequence rollback.

## 23. Remaining Risks

This is local integration evidence, using one real Chrome build and representative journeys. It is not a load, production deployment, broad cross-browser or formal accessibility certification. The existing access-token lifetime and tab session design remain unchanged. External completion was not repeated beyond start/progress/restart; the existing full business regression covers later behavior. No unresolved cross-layer inconsistency or integration defect remains.

## 24. Phase 5.2 Handoff

Human review should inspect these reports, the recorded assertions and the one-line HTML diff. Backend/frontend remain available at their local URLs with the canonical seed restored. Recommended next action: **Human review → Phase 5.2 — Integration Regression & V1 Freeze**. Phase 5.2 and Version 2 have not been started.

## 25. Final Status

**PASS.** All Phase 5.1 acceptance criteria were met; 99 live audit assertions and frontend 20/backend 87 regression tests passed. Canonical data and frozen business/schema source remained intact.

- [x] PostgreSQL, backend and frontend start and communicate.
- [x] Authentication and representative read/write flows work through the stack.
- [x] FREE, external-provider and failure/rework journeys pass.
- [x] UI/API/DB states, versions, provider identities and persisted evidence agree.
- [x] Stale writes reject safely and restart preserves incomplete work.
- [x] Role boundaries, department scope and two-signer handover pass.
- [x] Final browser console/network and frontend/backend regression pass.
- [x] Canonical baseline restored; no new business feature/rule introduced.

## 26. Slide-ready Summary

- Real Chrome, Spring Boot and PostgreSQL passed full local integration.
- Authentication and role navigation passed for VTYT, BGD, KHOA and ADMIN.
- Seed reads and browser writes matched SQL and API IDs, states and versions.
- FREE journey ended COMPLETED / REPORTED / FINAL with two persisted signers.
- External approval/assignment/execution used provider #2 consistently; rework retained both attempts.
- Stale writes returned safe 409; frontend/backend restart preserved incomplete work.
- One missing-favicon issue fixed; frontend 20 and backend 87 tests passed.
- Cleanup restored 603 rows and 14/117/31 with V001–V006; awaiting human review.
