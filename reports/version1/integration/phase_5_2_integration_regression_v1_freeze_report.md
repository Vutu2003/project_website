# Phase 5.2 — Integration Regression & V1 Freeze

Date: 2026-09-28, Asia/Ho_Chi_Minh. **Status: PASS. V1 freeze decision: FROZEN.**

## 1. Objective

Confirm that accepted Phase 5.1 remains reproducible, run focused real-stack regression and the complete existing test suites, verify frozen metadata/source/data, and document the integrated V1 baseline. No new business implementation, Phase 6, Version 2, deployment or CI/CD work is included.

## 2. Starting Baseline

Read the accepted [Phase 5.1 report](phase_5_1_full_system_integration_report.md), [audit](phase_5_1_integration_audit.md), [backend freeze](../../../backend/docs/backend-business-freeze.md) and [frontend freeze](../../../frontend/docs/frontend-v1-freeze.md) before changes. Initial SQL counts, row fingerprints and migrations matched the accepted final database exactly: 14 tables, 117 columns, 31 FKs, V001–V006, 603 rows.

All reviewed Phase 5.1 files remained byte-identical. The new [Phase 5.2 driver](verify_phase_5_2.py) imports existing Browser/API/SQL/workflow helpers and redirects evidence to [phase_5_2_evidence.json](phase_5_2_evidence.json); it does not edit or rerun the Phase 5.1 entry point against its reviewed output.

Metadata checked: backend `medical-equipment-maintenance-backend` version `0.1.0-SNAPSHOT`, Java 17, Spring Boot 3.5.16; frontend `medical-maintenance-frontend` version `0.1.0`, consistent package/lock versions and resolved dependencies; exactly six migration source files. Versions were retained. Historical Maven/sidebar phase descriptions reflect their original phase and were not changed.

## 3. Startup Regression

Existing documented commands, from the repository root:

```bash
source scripts/use-toolchain.sh
./backend/scripts/setup-dev-db.sh
source .local-postgres/backend-dev.env
source .local-postgres/backend-security.env
```

Backend terminal, with those env files sourced:

```bash
env -u DEBUG java -jar backend/target/medical-equipment-maintenance-backend-0.1.0-SNAPSHOT.jar
```

Frontend terminal:

```bash
source scripts/use-toolchain.sh
npm run dev --prefix frontend
```

PASS. The idempotent PostgreSQL helper confirmed the existing project-owned cluster on `127.0.0.1:55432`; authenticated SQL connected. Backend health returned `{"status":"UP"}`, login HTTP was 200 at `http://localhost:5173/login`, and real cross-origin API calls/preflights succeeded. Existing services were then actually stopped/restarted for persistence verification, using the same startup commands and env files. No reset, reseed or schema change was needed.

The driver uses the already-running isolated Chrome/CDP setup documented in Phase 5.1. Recorded stages: `init`, `quick`, `happy`, restart services, `restart`, `cleanup`, complete frontend/backend regression, `final`. Frontend regression ran independently while browser checks were running. Raw secrets stayed in ignored mode-600 env files and process memory.

## 4. Authentication Regression

PASS for demo_vtyt → PHONG_VTYT, demo_bgd → BAN_GIAM_DOC, demo_khoa_noi → KHOA_PHONG/department 1, and demo_admin → ADMIN. Actual login/JWT `/api/auth/me` and frontend navigation matched accepted Phase 5.1. Real logout cleared sessionStorage for every role. An invalid primary credential caused 401, cleared the tab session and displayed readable expiry/login feedback.

## 5. Read Regression

PASS. Equipment #1/DEMO-EQ-001/name/department matched SQL, API and UI. Canonical plan #2 matched SQL/API/UI status CLOSED, plan version 6 and first item #3 COMPLETED/version 8. Equipment #4 history matched campaign/plan/item identities and states. Provider #7/name matched SQL, provider API and actual-provider display on both attempts. The target campaign's details were expanded in real Chrome.

Historical CLOSED seed records do not imply an implemented REPORTED → CLOSED command.

## 6. Write Regression

Exactly one isolated plan was created with title `SMOKE-P52-20260928111827-FREE`. Browser create → submit → BGD approve → FREE coverage #1 → start execution generated plan #256/item #349. SQL/API/UI comparisons agreed at each captured state and version. Server generated statuses, IDs, provider assignment and attempt number; no direct SQL success simulation was used.

## 7. Happy Path Regression

PASS. The same single smoke plan continued through progress → work completion → technical PASS → KHOA handover PASS with VTYT co-signer → report DRAFT → FINAL → history.

Final UI/API/DB: **item COMPLETED v5, plan REPORTED v7, report #44 FINAL**. Report counts were 1 completed and 0 repair-required. History retained the one actual-provider attempt, progress, both PASS acceptances and FINAL report reference. Final report narrative reflected the successful concurrent update used for the stale test.

## 8. Failure/Rework Regression

PASS using canonical evidence, as authorized by this phase. Re-read equipment #4/plan #2/item #3 live through SQL/API/browser. Execution #1/attempt 1 retained technical FAIL and its conclusion; execution #2/attempt 2 remained a separate PASS attempt. State history retained REWORK_REQUIRED and the later start; browser displayed both attempts, prior failure and rework chronology. No canonical row was altered.

The accepted Phase 5.1 live FAIL → REWORK_REQUIRED → new-attempt evidence remained byte-identical. The full existing execution/business regression also passed; no second smoke plan or extra failure workflow was needed.

## 9. Optimistic Lock Regression

Browser A held plan version 5. API B changed its report with version 5, returning version 6. Browser A's old PUT returned 409 and “Xung đột phiên bản”. SQL plan/report fingerprint stayed unchanged and exactly one report existed. Reload fetched current narrative/version, then finalization used it and returned version 7.

After restart, another rejected stale API PUT confirmed exact `OPTIMISTIC_LOCK_CONFLICT`. No duplicate/partial update or automatic retry occurred.

## 10. Role/Department Regression

PASS. Frontend guards and backend authorization denied VTYT/KHOA/ADMIN the BGD approval queue, while BGD was denied plan creation. BGD performed the real smoke-plan approval. ADMIN navigation still contains only the dashboard and gains no business command/account management.

KHOA department 1 opened allowed equipment #1 history and performed its handover. Foreign equipment #4 history returned 403 without the foreign name in UI/API error. Accepted role policy and historical-custody rules were unchanged.

## 11. Two-Signer Regression

PASS. Actual handover POST used primary KHOA Authorization, plus a distinct temporary VTYT token only in `X-VTYT-Authorization`. Recorded network evidence stores matching/presence booleans, never raw values. KHOA session stayed unchanged; only the primary sessionStorage credential remained and localStorage stayed empty. Signer fields cleared/disappeared. SQL stored both signer identities and confirmation timestamps on one PASS handover record.

## 12. Restart Persistence

Both services were actually restarted: backend PID 23267 → 38530; frontend PID 23451 → 38684. PostgreSQL PID 14264 stayed running.

Reopened smoke report/campaign matched the complete saved plan/item/report/attempt/history response exactly, including versions and persisted timestamps. FINAL report and REPORTED plan rendered correctly after reconnect. One `/api/auth/me` connection-refused entry occurred while backend was deliberately down; it was classified as expected restart downtime. Subsequent login/reopen and final canonical browsing were clean.

## 13. Frontend Regression

```bash
npm run build --prefix frontend
npm run lint --prefix frontend
npm run test --prefix frontend
```

**PASS / PASS / 20 tests passed in 8 files**. No frontend source or test was changed in this phase.

## 14. Backend Regression

With the project toolchain and both ignored env files sourced:

```bash
env -u DEBUG mvn -f backend/pom.xml test
```

**87 tests, 0 failures, 0 errors, 0 skips; BUILD SUCCESS; 33.045 seconds.** Complete existing suite, without test edits. Guarded smoke cleanup ran first because existing suites assert canonical seed counts; this avoided contaminating fixed-count assertions.

| Suite | Tests | Failures | Errors | Skips |
| --- | --- | --- | --- | --- |
| BackendFoundationIntegrationTest | 4 | 0 | 0 | 0 |
| PersistenceMappingAuditTest | 1 | 0 | 0 | 0 |
| PersistenceSmokeTest | 2 | 0 | 0 | 0 |
| ApiExceptionHandlerTest | 1 | 0 | 0 | 0 |
| ApiIntegrationTest | 9 | 0 | 0 | 0 |
| ApiQueryAuditTest | 1 | 0 | 0 | 0 |
| RepositoryIntegrationTest | 9 | 0 | 0 | 0 |
| RepositoryQueryAuditTest | 1 | 0 | 0 | 0 |
| CorsIntegrationTest | 2 | 0 | 0 | 0 |
| SecurityIntegrationTest | 8 | 0 | 0 | 0 |
| WorkflowReadIntegrationTest | 3 | 0 | 0 | 0 |
| BackendBusinessFinalIntegrationTest | 11 | 0 | 0 | 0 |
| ExecutionAcceptanceIntegrationTest | 13 | 0 | 0 | 0 |
| PlanningApprovalIntegrationTest | 8 | 0 | 0 | 0 |
| ProviderRoutingIntegrationTest | 14 | 0 | 0 | 0 |

## 15. Database Freeze Check

Guarded cleanup checked the exact single smoke plan ID, run-prefixed title and demo_vtyt ownership, then removed only its report/acceptance/progress/execution/approval/status-history/item dependencies and plan. Final full-suite cleanup was also verified.

**14 business tables / 117 columns / 31 FKs / six successful V001–V006 migrations / 603 rows**. Zero SMOKE-P52 text records across all 14 business tables. Canonical per-table row fingerprints, schema counts and migration history matched the accepted baseline exactly both after smoke cleanup and after complete backend regression. Source manifest confirmed no migration, schema-source or seed change. Identity sequences were allowed to advance.

## 16. Source Freeze Check

All 267 captured backend/frontend source, tests, configuration, metadata, migration, seed and existing-doc files matched their starting SHA-256 values. The current business-source hashes also matched the accepted Phase 5.1 evidence. All reviewed Phase 5.1 artifacts were byte-identical.

New files are confined to Phase 5.2 reports, evidence, manifest and driver under `reports/version1/integration/`. No product source changed. Repository currently has no Git commit; [manifest](version1_source_freeze_manifest.json) identifies the source content with digest `9465a5e2945a01d76ab8356810e71cf13014e5806fb1837723cad1b12d2cbd8c`. No commit, tag or release version bump was invented.

## 17. Problems Found

No real V1 integration defect was found. The reused Phase 5.1 harness's limited plan-label map omitted historical CLOSED seed status; the new driver needed that existing UI label for canonical plan #2. This was an audit-tool coverage issue.

Expected negative 401/403/409, the absent-report 404 probe, and the single intentional-restart reconnect error were classified. Zero uncaught JS exceptions, React warnings, CORS failures or credential logs occurred. A settled final page generated no further API requests over 2.5 seconds.

## 18. Fixes Applied

No product fix was required. Added CLOSED → “Đã đóng” only to the imported map in the new Phase 5.2 driver's memory; reviewed helper/evidence and product files remained unchanged. Reused the browser/API/SQL workflow implementation rather than creating another browser framework. No business rule or test was changed to force a pass.

## 19. Known V1 Limitations

- No ADMIN account management.
- No Repair V2 workflow after REPAIR_REQUIRED.
- No attachment management.
- No notifications.
- No production deployment.
- No completed CI/CD requirement or pipeline.
- No formal load certification.
- No formal WCAG certification.
- Local tab-scoped sessionStorage JWT approach remains in place; same-origin JavaScript can read it.
- No refresh token.
- No implemented REPORTED → CLOSED command. Historical seeded CLOSED records remain readable.

These are accepted scope boundaries. Functional verification is local, synthetic-data and single-Chrome evidence; it does not certify production or cross-browser behavior.

## 20. V2 Backlog

| ID | Planned scope | Status |
| --- | --- | --- |
| V2-ADMIN-01 — Account Management | Account list; create account; activate/deactivate; assign role; assign department; reset password. Prefer disabling accounts over physical deletion to preserve references and history. | Backlog only; not implemented |
| V2-REPAIR-01 — Repair Workflow | Define the workflow after REPAIR_REQUIRED, separately from Maintenance V1. | Backlog only; not implemented |

## 21. V1 Freeze Decision

**FROZEN.** The integrated V1 baseline is recorded in [version1_integration_freeze.md](version1_integration_freeze.md) and its [source manifest](version1_source_freeze_manifest.json). Phase 5.1 remains reproducible and preserved; all Phase 5.2 required checks passed. There is no unresolved integration defect, source/schema drift, authorization leak or cross-layer state disagreement.

## 22. Recommended Next Step

**Human review → Phase 6 — NFR / Testing**, or open **Version 2 planning after V1 freeze review**. Leave this frozen system and its evidence available for review. No Phase 6, Version 2, deployment or CI/CD implementation was started.

## 23. Final Status

**PASS** with 73 successful live assertions, frontend 20/20 and backend 87/87. Canonical data and reviewed source/evidence remain intact.

- [x] Stack starts normally.
- [x] Authentication regression passes.
- [x] Representative read passes.
- [x] Representative write passes.
- [x] One complete happy path passes.
- [x] Failure/rework regression passes using live canonical evidence.
- [x] Stale 409 works.
- [x] Role boundaries pass.
- [x] Department scope passes.
- [x] Two-signer handover passes.
- [x] Restart persistence passes.
- [x] Frontend build/lint/tests pass.
- [x] Full existing backend suite passes.
- [x] Database baseline restored.
- [x] No schema/migration drift.
- [x] No unresolved integration defect.
- [x] Freeze documents created.

## 24. Slide-ready Summary

- Frozen V1 frontend/backend/PostgreSQL remained reproducible and consistent.
- All four roles passed login/navigation/logout and representative authorization checks.
- Canonical equipment, plan, provider and history reads matched SQL/API/UI.
- One FREE smoke plan completed to COMPLETED / REPORTED / FINAL with two signers.
- Canonical failed/rework attempts remained distinct; stale 409 and reload passed.
- Backend/frontend restart reconstructed identical persisted workflow state.
- Frontend build/lint/20 tests and full backend 87 tests passed.
- Exact 603-row baseline restored; source/evidence preserved; V1 FROZEN for review.
