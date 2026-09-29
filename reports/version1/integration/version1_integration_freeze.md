# Version 1 — Integration Freeze

## 1. Freeze Date

**2026-09-28, Asia/Ho_Chi_Minh. Decision: FROZEN.** Phase 5.2 regression completed at `2026-09-28T11:22:08+0700`. Phase 5.1 is accepted; Phase 5.2 is PASS and ready for human review.

Source baseline identifier: `9465a5e2945a01d76ab8356810e71cf13014e5806fb1837723cad1b12d2cbd8c`. The [source manifest](version1_source_freeze_manifest.json) records SHA-256 values for 267 source, configuration, metadata, schema, seed, test and existing documentation files. This repository has no Git commit yet; this freeze identifies content by hashes and does not claim a commit/tag.

## 2. System Scope

Medical Equipment Maintenance Management System / Hệ thống Quản lý Bảo trì Trang thiết bị Y tế. Frozen Maintenance V1 covers UC01–UC12 through React frontend ↔ Spring Boot backend ↔ PostgreSQL. Existing schema, business states, role policy, transaction boundaries and command contracts remain unchanged.

FREE and director-approved external maintenance, execution/rework evidence, two-signer handover, reporting and scoped history are included. REPAIR_REQUIRED is the V1 repair hand-off boundary. Phase 6, Version 2, deployment and CI/CD have not been started.

## 3. Database Baseline

PostgreSQL 16, local private cluster `127.0.0.1:55432`; database `medical_maintenance_backend_dev`. **14 business tables / 117 columns / 31 FKs / 603 canonical business rows.** Flyway's internal history table is excluded from business totals.

Only these six successful migrations exist:

- V001 — master data.
- V002 — planning and coverage.
- V003 — approval.
- V004 — execution and acceptance.
- V005 — reporting and status history.
- V006 — required indexes.

Source files, canonical row fingerprints and migration history match the accepted Phase 5.1 baseline. No schema or seed modification occurred. Identity sequences may advance; none was reset.

## 4. Backend Baseline

Java 17; Spring Boot 3.5.16; PostgreSQL persistence through JPA/Hibernate; Flyway migrations; JWT security; named business commands and typed reads/error responses. Artifact `medical-equipment-maintenance-backend` keeps its existing development version `0.1.0-SNAPSHOT`. “Version 1” identifies the frozen system scope; package versions were not bumped.

Backend health: `http://localhost:8080/actuator/health` → UP. The [backend business freeze](../../../backend/docs/backend-business-freeze.md) remains the authoritative detailed contract. Full existing suite: **87 tests, 0 failures, 0 errors, 0 skips**.

## 5. Frontend Baseline

Existing React/TypeScript/Vite SPA at `http://localhost:5173`, with role navigation, route guards, central API client, safe errors, server-version commands and shared attempt/history rendering. Package `medical-maintenance-frontend` remains `0.1.0`.

The lockfile resolves React 19.3.0, React Router 7.18.4, TypeScript 5.9.3, Vite 7.3.6 and Vitest 3.2.7. See [frontend V1 freeze](../../../frontend/docs/frontend-v1-freeze.md). Build, lint and **20 tests in 8 files** passed. Phase 5.2 made no frontend product change.

## 6. UC01–UC12 Coverage

| UC | Frozen capability | Primary role |
| --- | --- | --- |
| UC01 | Create maintenance plan/items | PHONG_VTYT |
| UC02 | Edit eligible plan, retain audited items | PHONG_VTYT |
| UC03 | Submit plan | PHONG_VTYT |
| UC04 | Approve/request plan revision | BAN_GIAM_DOC |
| UC05 | Verified coverage routing | PHONG_VTYT |
| UC06 | External-provider draft/proposal | PHONG_VTYT |
| UC07 | External-provider decision | BAN_GIAM_DOC |
| UC08 | Numbered execution attempts/progress | PHONG_VTYT |
| UC09 | Technical acceptance | PHONG_VTYT |
| UC10 | Scoped handover; two signers for PASS | KHOA_PHONG + PHONG_VTYT |
| UC11 | DRAFT/FINAL maintenance report | PHONG_VTYT; BAN_GIAM_DOC read |
| UC12 | Equipment maintenance history | PHONG_VTYT, BAN_GIAM_DOC, scoped KHOA_PHONG |

Existing baseline coverage is retained. Phase 5.2 checks one compact full path, canonical failure evidence and focused security/read behavior; it does not reimplement these use cases.

## 7. Authentication / Role Model

Real demo logins and `/api/auth/me` passed for `demo_vtyt`, `demo_bgd`, `demo_khoa_noi`, and `demo_admin`, resolving PHONG_VTYT, BAN_GIAM_DOC, KHOA_PHONG and ADMIN respectively. Navigation matched accepted Phase 5.1 exactly. Logout clears the tab credential; invalid/expired primary sessions return 401 and readable login feedback.

VTYT owns maintenance commands, BGD owns decisions, and KHOA owns scoped handover/history. ADMIN has no invented business command or account-management UI; existing accepted generic authenticated read behavior remains unchanged. Backend authorization remains authoritative.

## 8. Department Scope

KHOA's historical campaign/handover scope follows `department_id_at_plan`; current custody and historical DTO masking remain as documented in the backend freeze. Live department-1 history for equipment #1 was allowed; foreign equipment #4 returned 403 without its name in UI/error output. Existing scope regression passed.

## 9. Optimistic Locking

MaintenancePlan and MaintenancePlanItem retain server versions. Commands use the version last loaded from the server; successful writes refresh authoritative state. Stale report PUT returned **409 OPTIMISTIC_LOCK_CONFLICT**, kept one report and unchanged persisted rows, and offered reload. Reload retrieved current narrative/version before finalization. No automatic stale-write retry or new locking semantics was introduced.

## 10. Two-Signer Handover

One live PASS handover kept primary KHOA Authorization and used temporary VTYT authentication only in `X-VTYT-Authorization`. The KHOA session remained unchanged, only its primary sessionStorage credential remained, localStorage stayed empty, and signer inputs were cleared. SQL retained both signer identities and confirmation timestamps on the handover record. Raw credentials are absent from evidence.

## 11. Reporting / History

One DRAFT/FINAL report per eligible plan. Finalization requires terminal V1 item outcomes and nonblank work-done narrative, then moves AWAITING_REPORT → REPORTED. COMPLETED and REPAIR_REQUIRED counts remain separate; history retains campaigns, numbered attempts, providers, progress, acceptance and report references.

Live Phase 5.2 plan #256/item #349/report #44 ended **REPORTED v7 / COMPLETED v5 / FINAL**, with counts 1 completed and 0 repair-required. Its full plan/item/report/campaign response survived backend and frontend restart exactly before smoke cleanup. Canonical plan #2's older CLOSED status is retained seed data; there is no new closing command.

## 12. Integration Evidence

Accepted evidence is preserved byte for byte:

- [Phase 5.1 full integration report](phase_5_1_full_system_integration_report.md).
- [Phase 5.1 audit](phase_5_1_integration_audit.md).
- [Phase 5.1 evidence](phase_5_1_evidence.json) and [reviewed helper](verify_phase_5_1.py).

New evidence: [Phase 5.2 report](phase_5_2_integration_regression_v1_freeze_report.md), [Phase 5.2 records](phase_5_2_evidence.json), [driver importing reviewed helpers](verify_phase_5_2.py), and [freeze manifest](version1_source_freeze_manifest.json). Phase 5.2 recorded 73 successful live assertions. Failure regression re-read canonical item #3/executions #1 and #2 in SQL, API and real Chrome, while retaining the accepted Phase 5.1 live FAIL/rework evidence.

Browser checks found no uncaught JS error, React warning, CORS failure or credential logging. Deliberate negative requests and one reconnect during intentional backend downtime were classified; final restored-seed browsing was clean and had no repeating fetch loop.

## 13. Regression Results

| Check | Final result |
| --- | --- |
| Real startup/connectivity/auth/read/write | PASS |
| Compact full FREE path/report/history | PASS |
| Canonical failure/rework evidence | PASS |
| Stale 409, role/scope and two-signer | PASS |
| Backend and frontend restart persistence | PASS |
| Frontend build/lint | PASS / PASS |
| Frontend tests | 20 passed; 8 files |
| Full backend suite | 87 passed; 0 failures/errors/skips |
| Canonical DB and source/evidence preservation | PASS |

Regression commands, with the existing toolchain and ignored security/database env files sourced:

```bash
npm run build --prefix frontend
npm run lint --prefix frontend
npm run test --prefix frontend
env -u DEBUG mvn -f backend/pom.xml test
```

The smoke plan was cleaned before the backend suite because existing tests assert canonical fixture counts; neither tests nor fixtures were changed to force a pass.

## 14. Canonical Data Baseline

A guarded transaction removed only the single owned Phase 5.2 smoke plan and its dependencies. Post-cleanup and post-full-regression per-table fingerprints were identical to the original accepted baseline. All 14 business tables contained zero SMOKE-P52 records.

| Business table | Canonical rows |
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

## 15. Known V1 Limitations

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

These are accepted V1 boundaries, not Phase 5.2 failures. Local single-browser functional evidence does not claim production or cross-browser certification.

## 16. V2 Backlog

| ID | Planned scope | Status |
| --- | --- | --- |
| V2-ADMIN-01 — Account Management | Account list; create account; activate/deactivate; assign role; assign department; reset password. Prefer disabling accounts over physical deletion to preserve references and history. | Backlog only; not implemented |
| V2-REPAIR-01 — Repair Workflow | Define the workflow after REPAIR_REQUIRED, separately from Maintenance V1. | Backlog only; not implemented |

## 17. Freeze Statement

**Maintenance Version 1 is FROZEN at this documented content baseline.** Phase 5.2 is PASS; no unresolved integration defect, authorization leak, state disagreement, source drift or migration drift remains. Phase 5.2 added reports/evidence and a driver reusing accepted helpers; no product source or test was changed.

Preserve the source manifest and reviewed integration evidence. Any future authorized product change must state its scope and validate against this baseline; this freeze does not authorize Phase 6 or Version 2 implementation.

## 18. Next Phase Handoff

Human review should inspect the Phase 5.2 report, manifest and preserved evidence. The real frontend/backend remain available with canonical data restored. After review, choose **Phase 6 — NFR / Testing** or **Version 2 planning**. No next-phase work has been started.
