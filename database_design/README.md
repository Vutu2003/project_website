# Frozen V1 Database Design

This directory contains **design documentation only**. The Phase 1.1-F freeze is **PASS for the documented university V1 scope**: 14 tables, 31 FKs, 117 columns. No schema, migration, seed data or application code was created.

## Implementation authority order for Phase 1.2

1. `phase_1_2_implementation_contract.md`
2. `data_dictionary.md`
3. `erd.md`
4. `database_constraints.md`
5. `design_decisions.md`
6. `database_traceability_matrix.md`

The business source of truth remains `docs/system_analysis_v1.pdf`. Hospital forms in `docs/temple.pdf` support fields and terminology. The older `reports/phase_1_1_database_design_report.md` is a **pre-freeze historical report** and does not override final files. If a later source interpretation conflicts with the freeze, stop Phase 1.2 and record a reviewed amendment.

## Reading order for review

1. `../reports/phase_1_1_database_design_freeze_report.md` — self-contained conclusion.
2. `design_freeze_diff.md` — all 20 former tables audited and before/after metrics.
3. `data_dictionary.md`, `erd.md`, `database_constraints.md` — exact schema contract.
4. `design_decisions.md`, `design_issues.md`, `open_questions.md` — policies and source limits.
5. `data_requirements.md`, `conceptual_data_model.md`, `logical_data_model.md`, `normalization.md`, `database_nfr_design.md`, `index_strategy.md`, `database_traceability_matrix.md` — derivation and technical rationale.
6. `../reports/phase_1_1_database_slide_source.md` and `../reports/phase_1_1_database_viva_notes.md` — presentation material.

## Freeze boundary

V1 covers maintenance only. REPAIR_REQUIRED preserves a V2 hand-off. Upload attachments, generalized multi-role RBAC, full contract finance and Repair V2 are outside this frozen schema. No implementation blocker remains for the supplied V1 specification; non-blocking hospital policy questions are listed in `open_questions.md`.

