# Backend

Phase 2.6 freezes the backend foundation for the Medical Equipment Maintenance Management System. Phase 3.1–3.4 complete the V1 backend business workflows UC01–UC12 over the existing 14-table schema. The stable frontend-facing contract is in [backend-business-freeze.md](docs/backend-business-freeze.md); use the [planning](docs/planning-approval-workflow.md), [routing](docs/provider-routing-workflow.md), [execution/acceptance](docs/execution-acceptance-workflow.md), and [reporting/history](docs/reporting-history-workflow.md) guides for commands, roles, states and errors.

## Stack

Java 17, Spring Boot 3.5.16, Maven 3.9.11, PostgreSQL 16, Flyway 11.7.2, Hibernate 6.6.53.Final, Spring Security 6.5.11 and JJWT 0.13.0. The project-local Java/Maven tools are selected by `source scripts/use-toolchain.sh` from the repository root.

## Architecture

`HTTP → Spring Security → controller → DTO/mapper + repository → JPA/Hibernate → PostgreSQL`. Flyway alone creates schema; Hibernate validates it. Phase 3 business services sit between command controllers and repositories; existing read routes retain their focused repository reads. Read [backend-architecture.md](docs/backend-architecture.md), [persistence-model.md](docs/persistence-model.md), [repository-query-guide.md](docs/repository-query-guide.md), [api-foundation-guide.md](docs/api-foundation-guide.md) and [security-guide.md](docs/security-guide.md) for details.

## Prerequisites

Linux/Bash, local PostgreSQL 16 binaries, Java 17 and Maven via the project toolchain, Python 3 with `bcrypt` (`python3-bcrypt`), and `curl`. Maven Central access is needed when dependencies are not cached. The private cluster uses `127.0.0.1:55432`; it is separate from a system PostgreSQL server. The generated `.local-postgres/` directory is ignored by Git.

## Environment variables

| Variable | Use | Source/default |
| --- | --- | --- |
| `DB_USERNAME`, `DB_PASSWORD` | Required DB login | Generated in ignored `.local-postgres/backend-dev.env` |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Dev database address | Defaults: `127.0.0.1`, `55432`, `medical_maintenance_backend_dev` |
| `JWT_SECRET` | Required Base64 signing key, at least 32 random bytes | Generated in ignored `.local-postgres/backend-security.env` |
| `JWT_EXPIRATION_SECONDS` | Access-token lifetime | Default 3600; allowed 60–3600 |
| `APP_PORT` | HTTP listen port | Default 8080; server binds to `127.0.0.1` |
| `FRONTEND_ORIGIN` | Exact allowed browser origin for `/api/**` CORS | Default `http://localhost:5173`; no wildcard |
| `SPRING_PROFILES_ACTIVE` | Local JDBC profile | Generated `dev`; shared config defaults to `dev` |

[`.env.example`](.env.example) contains placeholders only. Never commit or print the generated secret values. The local files are mode 600.

## Start PostgreSQL

Run from the repository root:

```bash
source scripts/use-toolchain.sh
./backend/scripts/setup-dev-db.sh
./backend/scripts/setup-demo-login.sh --env-only
source .local-postgres/backend-dev.env
source .local-postgres/backend-security.env
```

The first script creates or starts only the project-owned PostgreSQL 16 cluster and its dedicated backend dev DB. The second creates/reuses a local JWT key and four demo passwords; it does not touch database rows in `--env-only` mode.

## Reset development database

To deliberately replace only `medical_maintenance_backend_dev`, stop the app and run `./backend/scripts/reset-dev-db.sh --yes`. This discards backend demo rows and password hashes; re-run migration, seed and login bootstrap below. The separate Phase 1 database is not reset.

## Run migrations

On a new or reset backend DB, with both env files sourced:

```bash
env -u DEBUG mvn -q -f backend/pom.xml -DskipTests package
java -jar backend/target/medical-equipment-maintenance-backend-0.1.0-SNAPSHOT.jar
```

Wait for startup, then stop with Ctrl+C. Flyway applies only V001–V006 from `database/migrations/`. Starting again validates checksums and applies nothing new. `ddl-auto=validate` prevents Hibernate schema creation.

## Load demo data

After the six migrations and with the app stopped, run:

```bash
./backend/scripts/seed-dev-db.sh
```

This loads the six Phase 1.3 synthetic seed files into an empty migrated backend DB as one transaction: 603 rows. It is not a Flyway migration or a production import.

## Setup demo login

```bash
./backend/scripts/setup-demo-login.sh
source .local-postgres/backend-security.env
```

The script verifies the isolated backend DB and updates only the BCrypt password hashes of `demo_vtyt`, `demo_bgd`, `demo_khoa_noi` and `demo_admin`. Their raw passwords stay in the ignored local security env file. See [demo-accounts.md](docs/demo-accounts.md). Repeat this step after each reset and seed.

## Run backend

```bash
env -u DEBUG mvn -f backend/pom.xml spring-boot:run
```

Or run the packaged JAR with `java -jar backend/target/medical-equipment-maintenance-backend-0.1.0-SNAPSHOT.jar`. Keep both env files sourced in the same shell. The dev server listens on loopback port 8080 unless `APP_PORT` overrides it.

## Health check

`curl -fsS http://127.0.0.1:8080/actuator/health` should return `{"status":"UP"}` without a token. Only Actuator health is exposed.

## Login

With `.local-postgres/backend-security.env` sourced, submit the synthetic password over the local HTTP endpoint:

```bash
login_json=$(python3 - <<'PY' | curl -fsS -H 'Content-Type: application/json' --data-binary @- http://127.0.0.1:8080/api/auth/login
import json, os
print(json.dumps({'username': 'demo_vtyt', 'password': os.environ['DEMO_VTYT_PASSWORD']}))
PY
)
access_token=$(printf '%s' "$login_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
```

The token lasts one hour. Avoid printing or sharing it. To access the pending director queue, log in as `demo_bgd` instead; other roles receive 403.

## Call protected API

```bash
curl -fsS -H "Authorization: Bearer $access_token" http://127.0.0.1:8080/api/auth/me
curl -fsS -H "Authorization: Bearer $access_token" 'http://127.0.0.1:8080/api/equipment?page=0&size=10'
```

All business `/api/**` paths require authentication. Eight Phase 2.4 GET paths remain read-only; their pagination, filters and errors are documented in the [API guide](docs/api-foundation-guide.md). Missing/invalid credentials return JSON 401; a valid role without queue access receives JSON 403.

## Run tests

After seed and demo-login setup, with both env files sourced:

```bash
env -u DEBUG mvn -f backend/pom.xml clean test
env -u DEBUG mvn -f backend/pom.xml clean package
```

The integration suite uses the real isolated PostgreSQL DB. `backend/target/` is ignored. No H2 or Testcontainers database is used.

## Project structure

- `src/main/java/.../persistence`: 14 entities, 11 enums and 14 repositories.
- `src/main/java/.../api`: read controllers, request/response DTOs, mappers and error handler.
- `src/main/java/.../security`: login, JWT filter, principal/context and access rules.
- `src/test/java`: foundation, mapping, repository, API and security integration tests.
- `scripts`: guarded DB setup/reset/seed and demo-login helpers.
- `docs`: architecture, layer guides and [Phase 3 handoff](docs/phase_3_handoff.md).

## Backend V1 scope and known limits

Phase 3.1–3.4 implement the V1 backend paths for UC01–UC12. KHOA_PHONG equipment, plan and history reads use current or historical department scope. The documented `REPORTED → CLOSED` edge has no source-defined closing command. There is no refresh-token flow or formal load benchmark. Phase 4.1 adds an exact development CORS origin and the frontend shell. See the [business freeze](docs/backend-business-freeze.md) and [handoff](docs/phase_3_handoff.md). Treat demo accounts and data as local fixtures only.
