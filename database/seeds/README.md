# Phase 1.3 Demo Seed

**THIS IS DEMO DATA.** It is not actual operational data from any hospital. Public manufacturer/model labels are reference facts only. All asset ownership, departments, users, serials, contracts, providers, coverage, approvals, maintenance events, dates and reports are synthetic. No patient or real employee information is present.

## Scope and provenance

- [Data sources](data_sources.md) separates public product references from invented project data.
- [Dataset catalog](dataset_catalog.md) records exact row counts and distributions.
- [Demo scenarios](demo_scenarios.md) maps coherent paths to future screens and APIs.
- Public model labels appear only where an official manufacturer page was checked. Generic models start with `DEMO-`.
- `password_hash` values are BCrypt-compatible hashes of discarded random values; no usable password or login credential is included.

## Ordered files

| Order | File | Content |
| --- | --- | --- |
| 01 | `01_master_reference.sql` | Departments, role accounts, fictional providers, equipment |
| 02 | `02_equipment_coverage.sql` | FREE, NOT_FREE and UNKNOWN evidence |
| 03 | `03_plans_and_items.sql` | Eight plans and 52 device participations |
| 04 | `04_approvals.sql` | Plan/vendor rounds and director decisions |
| 05 | `05_execution_and_acceptance.sql` | Numbered attempts, work logs and typed assessments |
| 06 | `06_reports_and_history.sql` | Narrative reports and all plan/item transitions |

The SQL is deterministically rendered by `build_demo_seed.py` using only Python's standard library. The seed runner executes the six checked-in SQL files in **one PostgreSQL transaction**. It requires an empty, freshly migrated database; re-running it on a populated database fails clearly. This avoids duplicate workflow rounds and audit events. For a full rebuild:

```bash
./database/scripts/reset_database.sh --yes
```

This command runs Phase 1.2 regression tests, the Phase 1.3 validation suite and schema audit. For demonstration queries:

```bash
source database/scripts/common.sh
psql -X -d medical_maintenance_db -f database/tests/demo_queries.sql
```

## Phase 1.2 seed transition

The previous small `database/seeds/demo.sql` was **replaced**, not layered on top. Its stable demo identifiers (`demo_vtyt`, `demo_bgd`, `demo_khoa_noi`, `demo_admin`, `DEMO-EQ-001`–`003`, the September demo plan and two providers) were kept as a compatibility cohort so the existing Phase 1.2 constraint tests still work. One old assertion that expected exactly one UNKNOWN coverage row was generalized to require at least one; the new dataset has five. No migration or schema definition changed.

## History size and retention

The 240 history rows exceed the suggested 80–120 because the seed includes 52 item creation events, eight plan lifecycles, 15 completed items and full rework paths. Omitting intermediate events would contradict BR05 and the frozen append-only history contract. The rows are coherent transition evidence, not filler. Verification fixtures roll back and do not add persistent rows.
