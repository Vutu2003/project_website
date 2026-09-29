# Database Implementation

## Requirements

- Ubuntu or Linux with PostgreSQL **16.x** client and server binaries. This checkout was verified with PostgreSQL **16.15**.
- Bash, Python 3, `psql`, `createdb`, `dropdb`, and `/usr/lib/postgresql/16/bin/{initdb,pg_ctl}`.
- No Docker, Spring Boot, cloud database, or external package is required.
- The Phase 0 Java/Node toolchain is unchanged. In a new project shell, use `source scripts/use-toolchain.sh` if those tools are needed.

## Local Database

- Database: `medical_maintenance_db`.
- PostgreSQL 16.15 runs as the current OS user in the private, Git-ignored `.local-postgres/` directory.
- Host: private Unix socket at `.local-postgres/run`; port: **55432** on that socket. The same private cluster also listens only on `127.0.0.1:55432` for SCRAM-authenticated backend-dev connections. The separate system `16/main` cluster runs on 5432, but requires PostgreSQL administration rights unavailable to this user. Scripts always verify their connection points to the project-owned cluster before touching the database.
- Local socket authentication is limited by the private parent directory (mode 700). No password or credential file is stored in Git.
- The database scripts are for a **development database only**. They do not affect the system cluster.

## Setup

From the repository root:

```bash
source scripts/use-toolchain.sh
./database/scripts/create_database.sh
```

`create_database.sh` initializes and starts the private cluster when needed, then creates the database once. The local data directory persists across shells and is ignored by Git.

## Apply Migrations

```bash
./database/scripts/migrate.sh
```

This command requires an **empty** `public` schema, applies `V001` through `V006` in filename order, and stops on SQL error. Each migration is wrapped in `BEGIN`/`COMMIT`. It intentionally creates no migration metadata table, preserving the exact 14-table business schema. For a fresh rebuild, use the reset command below. Do not replay migrations on a populated database.

## Apply Demo Seed

```bash
./database/scripts/seed.sh
```

The active Phase 1.3 dataset is described in [seeds/README.md](seeds/README.md), [its catalog](seeds/dataset_catalog.md), [scenario catalog](seeds/demo_scenarios.md), and [provenance table](seeds/data_sources.md). Six ordered SQL files are applied in one transaction. The seed requires an **empty, freshly migrated database**; use the reset command to regenerate it. The old small `seeds/demo.sql` was replaced. There are **no usable demo passwords**; backend authentication must provision credentials separately.

## Run Tests

```bash
./database/scripts/verify_database.sh
```

`verify_database.sh` runs the original Phase 1.2 SQL regression suite (70 assertions) and exact frozen-schema audit. The SQL fixtures roll back, leaving the seed intact. `validate_demo_data.sh` runs the 32 Phase 1.3 data-quality assertions, including chronology and workflow evidence. The audit writes [machine-readable schema evidence](../reports/database_design/phase_1_2_database_schema_audit.json). Any failure returns a nonzero exit code.

```bash
./database/scripts/validate_demo_data.sh
```

## Reset Development Database

```bash
./database/scripts/reset_database.sh --yes
```

**Development-only destructive command:** `--yes` is required. It drops only `medical_maintenance_db` on the verified private project cluster, recreates it, applies all migrations, seeds Phase 1.3 data, runs Phase 1.2 regression and schema audit, then validates Phase 1.3 data. To drop without rebuilding, use `./database/scripts/drop_database.sh --yes`. Business records in a real deployment must follow retention policy; these reset scripts are never for production or hospital data.

## Migration Order

| File | Purpose |
| --- | --- |
| `V001__master_data.sql` | Departments, providers, users, equipment |
| `V002__planning_and_coverage.sql` | Coverage, plan, plan item |
| `V003__approval.sql` | Approval request and action |
| `V004__execution_and_acceptance.sql` | Numbered attempts, progress, acceptance |
| `V005__report_and_history.sql` | Plan report and typed status history |
| `V006__required_indexes.sql` | Six CREATE NOW lookup indexes and two partial pending-request unique indexes |

Primary-key and unique constraints provide the other indexes. Deferred candidate indexes from `database_design/index_strategy.md` are intentionally absent.

## Schema Source of Truth

Use [the Phase 1.2 implementation contract](../database_design/phase_1_2_implementation_contract.md), followed by `data_dictionary.md`, `erd.md`, `database_constraints.md`, `design_decisions.md`, and `database_traceability_matrix.md`. The freeze is **14 tables, 31 FKs, 117 columns**. The old 20-table model is superseded.

String/enum values are `TEXT` plus exact `CHECK` lists; this avoids arbitrary length truncation. Dates use `DATE`; event times use `TIMESTAMPTZ`; keys use generated `BIGINT` identities. All FKs use `ON UPDATE RESTRICT ON DELETE RESTRICT`. Cross-row authorization, transition order, matching coverage/provider evidence, and atomic history writes remain domain-service obligations for Phase 2.

## Important Safety Note

Phase 2.1 added a loopback-only TCP endpoint and a separate Flyway-owned backend development database on the same private cluster. The original Phase 1 database still uses the private socket. The generated backend credential is kept in ignored `.local-postgres/backend-dev.env`, never in this repository. See `backend/README.md` for the new setup.
