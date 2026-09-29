# Medical Equipment Maintenance Management System

University software engineering project for a medical equipment maintenance management system. Source business documents are preserved in `docs/`.

## Current Status

| Phase | Status |
| --- | --- |
| Phase 0 — environment | PASS |
| Phase 1.1 — database design | PASS / FROZEN |
| Phase 1.2 — PostgreSQL implementation | PASS |
| Phase 1.3 — synthetic demo data | PASS |
| Phase 2.1 — Spring Boot foundation | PASS |
| Phase 2.2 — persistence mapping | Not started |

The frozen V1 schema has **14 business tables, 117 columns and 31 foreign keys**. The active Phase 1.3 dataset has 603 synthetic/reference-inspired rows. Phase 2.1 adds a runnable Spring Boot backend with PostgreSQL, Flyway and `/actuator/health`; it does not yet implement maintenance APIs, entities or authentication.

## Development Environment

For the configured local environment and start/stop commands, see [hướng dẫn chạy trên máy hiện tại](HUONG_DAN_CHAY.md) and [installation verification](reports/environment_installation_2026-09-29.md). Project-local PostgreSQL/curl packages are supported through `scripts/use-toolchain.sh`.

Ubuntu 24.04 with a project-local Java 17 and Maven toolchain. On a fresh checkout run `bash scripts/setup-toolchain.sh`, then `source scripts/use-toolchain.sh` in each Bash shell. PostgreSQL 16 uses the private project-owned cluster in ignored `.local-postgres/`. See [backend setup](backend/README.md) and [database setup](database/README.md) for commands.

## Repository Structure

- `docs/`: original business source documents.
- `database_design/`: frozen V1 design, dictionary, ERD and implementation contract.
- `database/migrations/`: authoritative V001–V006 PostgreSQL SQL files.
- `database/seeds/`: separate synthetic demo data and provenance documentation.
- `backend/`: Spring Boot 3.5 application, tests and development database helpers.
- `frontend/`: placeholder for a later phase.
- `reports/database_design/`: Phase 0 and Phase 1 reports and slide notes.
- `reports/backend/`: backend phase reports.
- `scripts/`: local toolchain setup and selection.

## Backend Quick Start

```bash
source scripts/use-toolchain.sh
./backend/scripts/setup-dev-db.sh
source .local-postgres/backend-dev.env
cd backend
env -u DEBUG mvn test
env -u DEBUG mvn spring-boot:run
```

The first app start applies the frozen schema to a separate empty backend development database through Flyway. Load Phase 1.3 data only when a demo fixture is needed; see `backend/README.md`.
