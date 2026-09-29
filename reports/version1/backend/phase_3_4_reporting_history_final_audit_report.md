# Phase 3.4 — Reporting, Equipment History & Final Backend Audit

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-26  
**Status:** **PASS**

## 1. Objective

Complete UC11–UC12, audit UC01–UC12 and BR01–BR05 end to end, then freeze the V1 backend business contract for frontend work. No schema or frontend feature was added.

## 2. Starting Point

Phases 3.1–3.3 had 71 passing tests and plans reached `AWAITING_REPORT` once every item was `COMPLETED` or `REPAIR_REQUIRED`. The 14-table, 117-column, 31-FK PostgreSQL schema and 603-row demo seed were already frozen.

## 3. UC11 Reporting

A `MaintenanceReportService` creates one DRAFT report per eligible plan, replaces draft narrative, finalizes it and serves a flat read DTO. PHONG_VTYT writes; PHONG_VTYT and BAN_GIAM_DOC read. No client actor ID, stored summary counter, new table or PDF export was added.

## 4. Report Eligibility

The service requires plan `AWAITING_REPORT`, at least one item, and every item `COMPLETED` or `REPAIR_REQUIRED`. It rejects APPROVED, IN_PROGRESS and REPORTED plans and a falsely marked AWAITING_REPORT plan with nonterminal items.

## 5. Report Lifecycle

The frozen enum has `DRAFT` and `FINAL`. Create/edit keep the plan AWAITING_REPORT and advance plan version; FINAL requires nonblank `workDone` and cannot be edited. One report per plan is checked in service and by the frozen unique constraint.

## 6. Report Outcome Counts

Response fields `completedCount` and `repairRequiredCount` are derived from plan items. An integration plan with one completed and one repair-required item returned **1 / 1**. Repair hand-off is never reported as completed.

## 7. Plan Report Transition

Finalization atomically changes report DRAFT→FINAL, plan `AWAITING_REPORT → REPORTED`, and appends one plan StatusHistory event. A test-only history failure leaves the report DRAFT, plan AWAITING_REPORT and no REPORTED history. `REPORTED → CLOSED` was not implemented: the source state diagram names the edge but UC11 does not define an actor or closing precondition.

## 8. UC12 Equipment History

`GET /api/equipment/{id}/maintenance-history` returns a read-only DTO with equipment identity, campaigns, plan/item outcomes, route/provider, numbered attempts, progress, typed acceptances, status history and concise report references. It returns 404 for missing equipment and 403 for an unauthorized existing one.

## 9. History Read Model

The service aggregates frozen entities without a persistence table or entity serialization. Campaigns sort newest first by plan creation time and stable IDs; attempts sort by attempt number; logs and status events sort by timestamp then ID.

## 10. Multi-campaign History

The Phase 1.3 DS-09 equipment (`DEMO-EQ-004`) returned multiple campaigns, including distinct COMPLETED and REPAIR_REQUIRED outcomes. No campaign was collapsed into the equipment's current state.

## 11. Multi-attempt History

Seeded two-attempt items and an isolated technical-FAIL→rework→PASS scenario retained both attempts, failed and successful assessments, progress and final report reference. The current attempt is never validated by an old attempt's PASS.

## 12. Department Scope

PHONG_VTYT/BAN_GIAM_DOC can see broad history; ADMIN cannot call UC12. KHOA_PHONG sees only campaigns whose `department_id_at_plan` matches its authenticated department. Current custody permits equipment access without inheriting another department's old campaigns. A former department retains its historical campaign after equipment moves; current custody is masked. Existing Khoa/Phòng equipment and plan list/detail/item GETs were also scoped to current or historical department membership.

## 13. Query Strategy / N+1

UC12 uses one equipment read, one item read and focused batch reads for executions, progress, acceptances, item/plan history and reports. To-one fetches cover plan/provider display data; no global EAGER or mega-join exists. Authenticated HTTP counts on the 603-row fixture were **7 SQL simple / 9 multi-campaign / 9 multi-attempt**. This demonstrates a bounded demo query shape, not formal NFR latency or production-scale performance.

## 14. UC01–UC12 Coverage

All 12 use cases have backend paths. The mandatory HTTP/JWT E2E test executed create→submit→approve→route→execute→progress→technical PASS→two-party handover PASS→draft/final report→history. A second test exercised external provider approval and a mixed completed/repair plan. Earlier phase tests cover edit/revision and other exception paths. See [full audit](phase_3_4_full_business_audit.md).

## 15. BR01–BR05 Audit

BR01 draft/revision edit and audited item-retention policy remain; BR02 plan/route gate remains; BR03 FREE/NOT_FREE provider evidence and actual provider match remain; BR04 current-attempt technical PASS and two-signer handover remain; BR05 plan/item transitions remain paired with append-only history. Full regression and database invariant queries passed.

## 16. Plan State Machine Audit

Implemented: DRAFT→SUBMITTED; SUBMITTED→APPROVED/REVISION_REQUIRED; REVISION_REQUIRED→DRAFT; APPROVED→IN_PROGRESS; IN_PROGRESS→AWAITING_REPORT; AWAITING_REPORT→REPORTED. No generic status shortcut exists. CLOSED remains in the frozen vocabulary without an invented close command.

## 17. Item State Machine Audit

All V1 route, vendor revision, execution, technical acceptance, handover, rework and repair hand-off edges remain as audited in Phases 3.1–3.3. REPAIR_REQUIRED is terminal and reportable, never COMPLETED. Unsupported edges are rejected by named commands.

## 18. Approval Round Audit

PLAN_APPROVAL and VENDOR_SELECTION preserve each submitted request and its one terminal director action. A final database query found **0 decided requests without actions**. Prior integration tests cover revision/resubmission and duplicate-pending rejection.

## 19. Execution / Acceptance Audit

Numbered attempts retain actual provider and append-only progress. Each type has one acceptance per attempt. Failed technical/handover assessments remain; rework creates a new attempt. A final invariant query found **0 COMPLETED items missing a current-attempt technical PASS and signed handover PASS**.

## 20. Reporting Audit

The draft/final split, one-report uniqueness, nonterminal-item block, separate counts, stale plan version, final immutability, role reads and rollback were tested. A final query found **0 FINAL reports attached to a plan outside REPORTED/CLOSED**.

## 21. Role Matrix

PHONG_VTYT owns planning, routing, work, technical acceptance and reporting; BAN_GIAM_DOC owns decisions and broad history/report reads; KHOA_PHONG owns scoped handover and history/read access; ADMIN has no UC12 or report role. Exact command/read matrix appears in the [audit](phase_3_4_full_business_audit.md). Phase 2 generic ADMIN reads remain an accepted foundation behavior and are listed as a V1 policy review item.

## 22. Department Isolation

UC10 and UC12 use historical item department, while equipment search also considers current custody. A department user cannot view a foreign equipment/detail/history or foreign plan/item list. The Khoa/Phòng DTO masks current custody for a former department; UC12 hides plan-wide history, coverage IDs and unfinished report references from it.

## 23. Transaction Audit

Planning, submission, director decisions, routing/proposals, execution, technical/handover assessment and report writes remain service-level transactions. Controllers contain no SQL or multi-row workflow decisions. Report-finalization and prior acceptance/approval rollback tests passed.

## 24. Optimistic Locking Audit

Only plan and item entities have `@Version`. Report draft/final commands require plan version; draft writes force its increment. Execution start requires both plan and item versions; other item state commands require item version. Stale inputs return 409 without partial records.

## 25. Security Regression

JWT/BCrypt, active-account reload, four-role login, expired/invalid-token 401, command 403, department scope and two authenticated handover signers remained covered by Phase 2/3 tests. The final JAR smoke logged no ERROR entry or visible Bearer token.

## 26. API Regression

Existing plan, item, approval, auth and execution endpoints retained their paths and DTO/error shapes. New commands use `/api`, not `/api/v1`. New report/history responses expose no JPA proxy, password hash, JWT, technical specification or entity graph.

## 27. Query Regression

Existing Phase 2 repository/API SQL audits passed. UC12 measured 7/9/9 SQL on simple, multi-campaign and multi-attempt demo histories, including authenticated account reload. These are query-shape checks on synthetic rows, not a claim of the source's larger NFR performance target.

## 28. Full Test Regression

After the clean rebuild, `mvn clean test` and an independent `mvn clean package` each passed **82/82 tests**, with 0 failures, 0 errors and 0 skips. This comprises Phase 2 **36**, Phase 3.1 **8**, Phase 3.2 **14**, Phase 3.3 **13**, and new Phase 3.4 **11**.

## 29. Database Regression

Final isolated backend dev metadata: **14 business tables, 117 columns, 31 FKs, six successful V001–V006 migrations, 603 canonical business rows**. No migration, schema, seed or entity column mapping changed. After cleanup there were **0 TEST/SMOKE plans**; four demo BCrypt hashes are the intentional local login setup.

## 30. Clean Rebuild

The isolated backend DB was reset, the packaged application applied unchanged Flyway V001–V006, six seed files loaded 603 synthetic rows, and four local demo logins were configured. Full tests and independent packaging then passed. The separate Phase 1 database and source PDFs were not modified.

## 31. Packaged JAR Smoke

The final JAR reported health UP. All four demo roles logged in. One HTTP flow created/approved/routed a plan, completed execution and two-party handover, finalized a report, and queried broad plus scoped history. Report was FINAL, plan REPORTED, and history retained audit and acceptance evidence. Smoke rows were deleted, restoring 603 rows.

## 32. Problems Found

The first test run started while the project-owned PostgreSQL cluster was stopped; starting it resolved connection refusal. Draft writes initially returned a plan version before Hibernate's deferred optimistic increment; switching to an immediate pessimistic force increment made the returned version usable. The prior generic Khoa/Phòng equipment/plan GETs were broader than NFR-SEC-02; scoped queries and masking closed that leak for department users.

## 33. Fixes Applied

Started the local cluster, made draft version increments immediate, added batch history reads and scoped equipment/plan queries, and tested wrong-department and historical-transfer behavior. The report/history DTOs are explicit and leave sensitive/internal fields out of JSON.

## 34. Known Limitations

`REPORTED → CLOSED` has no source-defined API precondition/actor; item removal remains rejected under the V1 audit policy. There is no frontend, V2 repair, attachment, refresh token or formal production-load proof. Phase 2 generic ADMIN read access predates this business freeze; UC11/UC12 use narrower explicit roles, and hospital policy should review other generic read routes before production.

## 35. Backend Business Freeze

The [business freeze](../../backend/docs/backend-business-freeze.md) records UC01–UC12, BR01–BR05, state graphs, actors, transaction/version rules, API inventory and V1 limits. It is the stable backend contract for Phase 4 review; no source/DDL drift was introduced.

## 36. Frontend Handoff

Frontend can use named commands, JWT/`/me`, role enums, 401/403, `ErrorResponse`, `PageResponse`, version fields, report counts and read-only history DTOs. It must refresh versions after writes, provide a second VTYT session for handover PASS, and keep failed/repair outcomes distinct. It must not write status or actor IDs directly.

## 37. Final Status

**PASS.** UC11–UC12, the mandatory full HTTP business flow, scoped history, 82/82 regression, clean PostgreSQL rebuild, final JAR smoke, audit invariants and frozen schema/seed all passed.

## 38. Slide-ready Summary

- Phase 3.4 PASS completes backend V1 UC01–UC12 and BR01–BR05.
- Report DRAFT/FINAL finalization moves plan to REPORTED; completed and repair counts stay separate.
- Equipment history preserves multiple campaigns, attempts and failed assessments.
- Historical department scope controls Khoa/Phòng reads.
- UC12 authenticated HTTP checks used 7/9/9 SQL on demo cases, with no obvious N+1.
- Clean rebuild, 82/82 tests, four-role JAR smoke and full UC01–UC12 path passed.
- Frozen database remains 14 tables / 117 columns / 31 FKs, V001–V006 and 603 rows.
- Backend business contract is ready for human review and Phase 4 frontend planning.
