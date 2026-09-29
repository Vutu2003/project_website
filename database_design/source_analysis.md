# Source Analysis

> Source extraction only. Final table/field authority is the Phase 1.1-F freeze in `database_design/README.md`.

## 1. Sources reviewed

| Source | Path | Scope | Integrity |
| --- | --- | --- | --- |
| System Analysis Specification — Version 1 | `docs/system_analysis_v1.pdf` | 53 pages: functional analysis, UC01–UC12, state lifecycle, NFRs, traceability | Read only; SHA-256 `8170720b365d683917b045b273d192d61e103995f281f61bd8ab9fb3f998736b` |
| Hospital forms excerpt | `docs/temple.pdf` | 32 PDF pages, printed source pages 63–94: QC01, QT01, QT02, QT04 forms | Read only; SHA-256 `4e012312cfe460320edbed8debb62874d810e2e2e9be813dc73dcc5ad51495a2` |
| Phase 0 report | `reports/phase_0_environment_report.md` | PostgreSQL 16.15 target and repository boundary | Read only |

The forms PDF contains forms, but **not the full text of procedure QT02-VTYT**. The System Analysis describes that procedure; its own statements remain the primary V1 specification. Printed footers on several forms show an unrelated BM12 code; the header/title identifies the form reviewed.

## 2. System Analysis summary relevant to data

Six modules: M1 planning, M2 approval, M3 provider selection, M4 execution, M5 acceptance/handover, M6 report/history. Four application actors: PHONG_VTYT, BAN_GIAM_DOC, KHOA_PHONG, ADMIN. External providers have no V1 login (Part I pp. 3–5; Part II p. 10). UC01–UC12 are detailed in Part II pp. 11–31. BR01–BR05 occur in Part I p. 6 and Part II p. 10; BR01 conflicts between those sections. Plan has eight states and item eleven (Part III pp. 32–38). NFR-SEC, AUDIT, PERF, REL, AVAIL, MAINT, and ENV are in Part IV pp. 39–51. V1 excludes repair workflow, detailed contract finance, vendor portal, digital legal signatures, and ERP/HIS integration.

## 3. Business forms reviewed

| Form | Purpose and actual groups | Database implications | Limits |
| --- | --- | --- | --- |
| BM01/QT02-VTYT, printed p. 86 | Annual maintenance/repair schedule: months × weeks with department names; director and head of VTYT signatures | Plan period and department scheduling; normalized plan items require equipment references from UC01 rather than literal form rows | The form schedules **departments**, not individual serial-numbered equipment; it combines maintenance and repair in title. V1 stores maintenance only. |
| BM02/QT02-VTYT, p. 87 | Request for director's opinion on inviting an external maintenance partner where free coverage does not apply; equipment description, narrative grounds, dated sign-off | Vendor proposal, coverage decision, proposed provider, approval request/action | No structured provider identifier, price, or contract terms. Provider selection fields are digital proposals. |
| BM03/QT02-VTYT, p. 88 | Generic report/plan template: work done, achieved/not achieved, causes, next work, solutions, recommendations, report number/date, head signature | Report narrative sections and reference to plan/result data | Blank generic headings; no fixed numerical result columns. Source specification itself flags incomplete detail (UC11). |
| BM06/QT01-VTYT, pp. 74–75 | Equipment handover/acceptance minutes: participant roles including hospital/company, equipment technical specifications, conclusion, signatures | Acceptance outcome, technical observations, participants, external signer names, attachment of original record | It is **not explicitly a post-maintenance technical-acceptance form**. UC09 adapts it; do not claim its wording defines a PASS/FAIL enum. |
| BM08/QT01-VTYT, p. 77 | Handover/acceptance of tools: handing VTYT unit, receiving department, participant names/positions, repeated equipment/quantity rows, notes and signatures | Handover participants and per-item evidence | It concerns tools and can list many items. UC10 adapts it for maintenance; one digital item acceptance may point to a scanned multi-item form. |
| BM09/QT01-VTYT, p. 78 | Equipment handover: handing/receiving parties, serial, model, manufacturer, receiving condition, accessories and warranty | Corroborates equipment identity and condition-at-handover vocabulary | Closer in title to equipment handover than BM08, but still an acquisition/handover form; SA UC10 expressly cites BM08. Which form governs post-maintenance handover is unresolved. |
| BM05/QT01-VTYT, pp. 72–73 | Purchase contract including warranty period, free repair coverage for defined defects and exclusions | Coverage basis, effective period, scope/reference | Warranty is not automatically identical to free **maintenance**; require verification before branch choice. |
| BM04/QT01-VTYT, p. 71 | Supplier criteria including warranty and repair/maintenance service | Provider vocabulary only | Procurement scoring is outside V1. |
| BM04–BM07/QT02-VTYT, pp. 89–92 | Repair proposal, repair notice and damage/liquidation records | Evidence that REPAIR_REQUIRED is a hand-off | No V1 repair tables. |
| BM01–BM02/QT04-VTYT, pp. 93–94 | Calibration/measurement planning | Out of V1 maintenance scope | No calibration tables. |

Other QT01 forms (procurement, stock, personnel custody, payment, liquidation) were inspected for boundary checks and do not justify V1 entities.

## 4. Source versus digital design

**Business source:** dated schedule, departments, equipment details, coverage/contract basis, external proposal, approver opinion, execution narrative, technical conclusion, participants, handover, report narrative. **Digital workflow design from the System Analysis:** plan/item states, approval rounds, progress logs, role-based visibility, status audit, optional scans. **Technical proposal in this design:** surrogate identifiers, optimistic versions, immutable submission snapshot, typed FKs for history/attachments, timestamps, verified coverage classification, historical display snapshots. These are never presented as original form fields.

## 5. Ambiguities and missing information

1. Full QT02 procedure text is unavailable in the repository; the supplied analysis is authoritative for V1 but some source wording cannot be independently checked.
2. BM01 has department scheduling, while UC01 plans individual equipment. Per-item dates and grouping are digital extensions.
3. BM06 and BM08 are adapted examples, not dedicated maintenance completion forms; BM09 is another equipment-handover source but is not cited by UC10; required signers, legal equivalence and exact fields need stakeholder confirmation.
4. BM03 is generic. Numeric report metrics and report approval are not sourced.
5. Contract warranty clauses do not prove free maintenance; explicit coverage verification and an UNKNOWN state are necessary.
6. Equipment code/serial uniqueness, plan period overlap, approval delegation, and post-final report correction have no settled source rule.

## 6. Database-relevant conclusions

Model entities and workflows rather than one table per form. Preserve historical decisions and the source document scans without storing binaries in PostgreSQL. Keep plan and item state separate. Store unknown coverage distinctly from verified non-free coverage. Treat repair only as a recorded V2 hand-off state. See `design_issues.md` and `open_questions.md` before implementation.

