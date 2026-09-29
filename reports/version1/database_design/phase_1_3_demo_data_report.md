# Phase 1.3 — Reference & Demo Data Preparation

**Project:** Medical Equipment Maintenance Management System  
**Result:** **PASS** on 2026-09-25  
**Scope:** Synthetic reference and workflow data for the frozen PostgreSQL 16 schema. No migration, table, column, foreign key, or state definition was changed.

## 1. Objective

Prepare a medium-sized, repeatable dataset that supports later backend and frontend work, API and workflow tests, equipment-history queries, screenshots, and presentation demos. All records must fit the Phase 1.1/1.2 design freeze.

## 2. Data Strategy

Public manufacturer pages supply only verified product and model labels. Hospital asset identities, department placement, users, providers, coverage, contracts, plans, decisions, work, assessments, reports, and dates are **synthetic demo data**. No private hospital operational or patient data was used. The six ordered SQL files are deterministic and run as one transaction on a freshly migrated database.

## 3. Sources Researched

Official product pages were checked for [Philips IntelliVue MX450](https://www.usa.philips.com/healthcare/product/HC866062), [Dräger Evita V600](https://www.draeger.com/en_uk/Products/Evita-V600), [GE HealthCare Venue Go](https://www.gehealthcare.com/en-us/products/ultrasound/point-of-care-ultrasound/venue/venuego), [Roche cobas c 311](https://diagnostics.roche.com/us/en/products/instruments/cobas-c-311-ins-2043.html), [Sysmex XN-Series](https://www.sysmex.com/en-ca/lab-solutions/hematology/xn-series), [B. Braun Spaceplus](https://catalogs.bbraun.com/en-01/c/PRODUCTS0000000574/spaceplus-system), and [Nihon Kohden Cardiolife TEC-8300](https://in.nihonkohden.com/en/products/resuscitation/defibrillators/cardiolife-tec-8300-series). Access date: 2026-09-25. The complete element-level record is in [`database/seeds/data_sources.md`](../database/seeds/data_sources.md).

## 4. Final Dataset Size

Counts are from PostgreSQL after the final clean rebuild.

| Entity | Count | Entity | Count |
| --- | ---: | --- | ---: |
| department | 8 | user_account | 15 |
| equipment | 40 | service_provider | 7 |
| maintenance_coverage | 35 | maintenance_plan | 8 |
| maintenance_plan_item | 52 | approval_request | 19 |
| approval_action | 15 | maintenance_execution | 30 |
| maintenance_progress_log | 90 | acceptance_record | 40 |
| maintenance_report | 4 | status_history | 240 |
| **Total** | **603** | | |

## 5. Departments

Eight fictional organizational units follow familiar Vietnamese hospital terminology: Cấp cứu, Hồi sức, Chẩn đoán hình ảnh, Xét nghiệm, Phẫu thuật, Nội, Ngoại, and Phòng Vật tư Y tế. They do not represent a real hospital's organization. The clinical departments own the demo devices; Phòng VTYT coordinates work and owns none.

## 6. Users / Roles

Fifteen invented accounts cover all four frozen roles: PHONG_VTYT 4, BAN_GIAM_DOC 2, KHOA_PHONG 7, ADMIN 2. Department users are scoped to their own department. BCrypt-compatible password hashes are non-login placeholders generated from discarded random inputs; no plaintext credential is provided.

## 7. Equipment Dataset

Forty devices span 15 categories: monitors 7; syringe pumps 5; infusion pumps 3; ECG 3; ultrasound 3; defibrillators, ventilators, X-ray systems, chemistry analyzers, hematology analyzers, centrifuges, anesthesia machines, electrosurgical units and autoclaves 2 each; oxygen concentrators 1. Placement: Hồi sức 8, Cấp cứu 7, Phẫu thuật 6, Xét nghiệm 6, Chẩn đoán hình ảnh 5, Nội 5, Ngoại 3. Publicly documented model names are used only when checked against an official manufacturer page. Unverified generic labels start with `DEMO-`; every asset code and serial number is invented.

## 8. Service Providers

Seven provider organizations have fictional demo names. Their assignments and coverage relationships are invented and do not imply real contracts with any manufacturer or hospital.

## 9. Coverage Distribution

There are 35 coverage records: FREE 16, NOT_FREE 14, UNKNOWN 5. Five other devices have no coverage row. Verified FREE and NOT_FREE records have the evidence required by the frozen schema; FREE routing uses its coverage provider. UNKNOWN and absent evidence leave an item unrouted. UNKNOWN is never silently treated as NOT_FREE.

## 10. Plan Distribution

Eight plans represent the complete frozen current-state set, one each: DRAFT, SUBMITTED, REVISION_REQUIRED, APPROVED, IN_PROGRESS, AWAITING_REPORT, REPORTED, CLOSED. Their items are grouped into synthetic maintenance campaigns. A revision example preserves two approval rounds.

## 11. Plan Item Distribution

Fifty-two item rows cover all 11 frozen current states.

| State | Rows | State | Rows |
| --- | ---: | --- | ---: |
| PLANNED | 15 | UNDER_CONTRACT | 8 |
| PENDING_PROPOSAL | 1 | WAITING_VENDOR_APPROVAL | 2 |
| ASSIGNED_EXTERNAL | 3 | IN_MAINTENANCE | 1 |
| AWAITING_TECHNICAL_ACCEPTANCE | 1 | AWAITING_HANDOVER | 1 |
| COMPLETED | 15 | REWORK_REQUIRED | 1 |
| REPAIR_REQUIRED | 4 | | |

Routes are UNDER_CONTRACT 26, EXTERNAL_APPROVED 8, and unassigned 18. Seventeen devices occur in more than one plan, allowing meaningful UC12 history without duplicating equipment master records.

## 12. Approval Scenarios

Nineteen requests include PLAN_APPROVAL (7 decided, 1 pending) and VENDOR_SELECTION (8 decided, 2 pending, 1 draft). Fifteen director actions contain 13 APPROVE and 2 REVISION_REQUIRED decisions. The plan revision keeps both request rounds; the external-provider path has a same-provider approval before assignment. Request, action, and transition timestamps are ordered.

## 13. Execution / Rework Scenarios

Thirty numbered execution attempts have 90 short progress logs. Seven items have two attempts. A technical failure and a separate handover failure retain their failed records and first attempts while later attempts reach completion. Provider identity follows the verified FREE route or approved external choice. Notes use general checks and do not claim product-specific clinical tolerances.

## 14. Acceptance Scenarios

Forty acceptance records comprise technical PASS 17, technical FAIL 7, handover PASS 15, and handover FAIL 1. A successful handover follows a technical PASS on the same attempt and includes the required scoped signers. COMPLETED items have both successful assessments for their latest attempt. Failed assessments remain visible.

## 15. Repair Hand-off Scenario

Four items end in REPAIR_REQUIRED with recorded damage/progress evidence and a history reason. In DS-06, `DEMO-EQ-004` moves from IN_MAINTENANCE to REPAIR_REQUIRED during a later campaign. This is the frozen V1 hand-off boundary; no Repair V2 table or downstream repair workflow was created.

## 16. Reports

Four concise BM03-style reports are present: two FINAL and two DRAFT. Work performed, achieved and incomplete results, causes, next work, and recommendations reflect the related plan/item outcomes. Report chronology follows the final relevant item results.

## 17. Status History

The 240 append-only events comprise 34 plan and 206 item transitions, including creation. Each path chains old and new states, actor, action, timestamp and required reason. This exceeds the suggested 80–120 range because retaining 52 item creation events, eight plan lifecycles, completed paths and rework loops is necessary for BR05-consistent audit evidence. No transitions were added merely to meet a quota.

## 18. Demo Scenario Matrix

| Scenario | Main UC / purpose | Main states | Final result |
| --- | --- | --- | --- |
| DS-01 | FREE contract route | PLANNED → UNDER_CONTRACT → IN_MAINTENANCE → COMPLETED | Provider matches coverage; accepted |
| DS-02 | External provider route | PENDING_PROPOSAL → WAITING_VENDOR_APPROVAL → ASSIGNED_EXTERNAL → COMPLETED | Prior director approval; accepted |
| DS-03 | Plan revision | DRAFT → SUBMITTED → REVISION_REQUIRED → DRAFT → SUBMITTED → APPROVED | Two rounds retained |
| DS-04 | Technical rework | IN_MAINTENANCE → REWORK_REQUIRED → IN_MAINTENANCE → COMPLETED | Failed and successful attempts retained |
| DS-05 | Handover failure | AWAITING_HANDOVER → REWORK_REQUIRED → IN_MAINTENANCE → COMPLETED | Failed handover retained |
| DS-06 | Repair hand-off | IN_MAINTENANCE → REPAIR_REQUIRED | Damage and reason retained |
| DS-07 | UNKNOWN coverage | PLANNED | No route or execution |
| DS-08 | Pending vendor decision | PENDING_PROPOSAL → WAITING_VENDOR_APPROVAL | No premature assignment |
| DS-09 | UC12 equipment history | Earlier COMPLETED; later REPAIR_REQUIRED | Two campaigns on one equipment record |

Record identifiers, roles and future screen/API uses appear in [`database/seeds/demo_scenarios.md`](../database/seeds/demo_scenarios.md).

## 19. Validation

[`database/tests/validate_demo_data.sql`](../database/tests/validate_demo_data.sql) passed **32 assertions, 0 failures**. Checks cover exact counts and schema, keys/FKs, department scope, model labels, coverage evidence and routing, all plan/item states, approval subjects and chronology, provider assignment, execution and log timing, acceptance order and signers, completed/rework/repair evidence, report chronology, allowed history chains, and UC12. Eleven demonstration queries in [`database/tests/demo_queries.sql`](../database/tests/demo_queries.sql) all returned rows.

## 20. Phase 1.2 Regression

The existing suite passed **70 assertions and 48 expected rejection cases, 0 failures**. The former small seed was replaced as a unit; stable legacy demo identifiers were retained. One test expectation changed from exactly one UNKNOWN coverage row to at least one because Phase 1.3 correctly contains five. No schema or migration was modified.

## 21. Clean Rebuild

`./database/scripts/reset_database.sh --yes` completed migrations, all six seed files in one transaction, Phase 1.2 tests, Phase 1.3 validation and schema audit. The terminal result was **CLEAN REBUILD PASS**. The schema audit reported **14 tables, 117 columns, 31 foreign keys**, plus 30 indexes and 99 constraints. The two source PDFs retained their original SHA-256 digests.

## 22. Data Provenance

**SOURCE-INSPIRED / PUBLIC REFERENCE:** manufacturer/model wording verified on official pages, and general equipment terminology. **SYNTHETIC DEMO:** all hospital association, department location, user, provider, asset/serial code, coverage/contract reference, maintenance event, approval, report, and date. The seed README begins with “THIS IS DEMO DATA”; `data_sources.md` classifies each element and gives original URLs and access date.

## 23. Remaining Limitations

- History is **240**, above the suggested 80–120 target; full audited paths were retained for consistency.
- Public product labels do not substantiate any hospital ownership, installed configuration, contract, or service relationship.
- Password hashes are placeholders; Phase 2 authentication work must provide an intentional login-seed strategy.
- The seed requires an empty migrated database. Use reset-and-seed for repeatable demos; it is not an incremental production import.

## 24. Final Status

**PASS.** All required scenarios A–G and two additional demonstrations are present; existing and new tests pass; the clean rebuild passes; the frozen schema remains 14 tables / 117 columns / 31 FKs. The documented history-count exception does not remove any workflow or audit evidence.

## 25. Backend / Frontend Handoff

Use [`database/seeds/README.md`](../database/seeds/README.md) to rebuild the fixture, [`database/seeds/dataset_catalog.md`](../database/seeds/dataset_catalog.md) for exact distributions, the scenario catalog for UI/API journeys, and the demo queries for equipment history, approval queues, work logs, acceptance, repair hand-off and report summaries. Rebuild before a demo rather than loading seed files onto an existing database. No backend or frontend implementation was started in this phase.

## 26. Slide-ready Summary

- Phase 1.3 **PASS**: 603 synthetic/reference-inspired records across the frozen 14-table schema.
- 40 devices across 15 categories and seven clinical departments; eight plans and 52 items.
- FREE, NOT_FREE and UNKNOWN coverage are distinct; UNKNOWN never auto-routes.
- All eight plan and 11 item current states are represented with coherent evidence.
- Nine scenarios cover normal contract/external paths, revision, rework, repair hand-off and UC12 history.
- Phase 1.2: 70 assertions and 48 expected rejections PASS; Phase 1.3: 32 assertions PASS.
- Clean rebuild and schema audit PASS; no schema changes or private hospital data.
