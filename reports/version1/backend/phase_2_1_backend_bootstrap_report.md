# Phase 2.1 — Backend Bootstrap & Database Integration

**Project:** Medical Equipment Maintenance Management System  
**Date:** 2026-09-25  
**Status:** **PASS**

## 1. Objective

Create only the backend foundation: a Java 17 Spring Boot application that builds, connects to PostgreSQL, applies the frozen SQL schema through Flyway, and reports health. This phase does not implement maintenance use cases or entity mapping.

## 2. Starting Point

Phase 1.1 froze the design at **14 tables, 117 columns, 31 foreign keys**. Phase 1.2 supplied six working SQL migrations and a private PostgreSQL 16 cluster; Phase 1.3 supplied six separate synthetic demo-seed files. The original `medical_maintenance_db` already contained the schema and 603 demo rows, but it had no Flyway history. `backend/` held only a placeholder README.

## 3. Backend Technology Stack

| Technology | What it is | Why used here |
| --- | --- | --- |
| Java **17.0.20.1** | Application language/runtime | Matches the frozen local toolchain and Spring Boot 3.5 requirements. |
| Spring Boot **3.5.16** | Java application framework | Starts the embedded server and configures JDBC, Flyway and Actuator. [Java 17 compatibility](https://docs.spring.io/spring-boot/3.5/system-requirements.html). |
| Maven **3.9.11** | Dependency/build tool | Resolves `pom.xml`, compiles, tests and packages a runnable JAR. |
| Spring Web | HTTP/server starter | Provides the embedded Tomcat server used by Actuator and later APIs. |
| Spring JDBC | DataSource/JdbcTemplate support | Checks the real database without creating JPA entities. |
| PostgreSQL JDBC **42.7.11** | JDBC protocol driver | Connects Spring's DataSource to the local PostgreSQL server. |
| Flyway **11.7.2** | Versioned migration tool | Applies V001–V006 and records them in `flyway_schema_history`. |
| Spring Boot Actuator | Operational endpoints | Exposes only `/actuator/health`. |
| PostgreSQL **16.15** | Relational database server | Stores the frozen business schema and enforces local SQL constraints. |

### Beginner Note — Spring Boot

Spring Boot starts the Java backend with an embedded web server and configures integrations from dependencies and settings. In this project it wires the DataSource, runs Flyway at startup, and provides the health endpoint.

### Beginner Note — Maven dependency

A dependency is a library the application needs. Maven reads `pom.xml` and downloads compatible versions; the Spring Boot parent manages most versions so each library does not need a separate manual version.

## 4. How the Pieces Work Together

```text
Browser / API client → Spring Boot → JDBC DataSource → PostgreSQL

Spring Boot startup → Flyway → V001…V006 SQL migrations → business schema
```

On startup, Spring Boot constructs the DataSource from environment variables. Flyway checks its history table, applies any missing schema migrations, and then the HTTP server becomes available. Actuator's built-in database health indicator checks that the DataSource can connect.

### Beginner Note — JDBC / DataSource

JDBC is Java's database connection standard. A DataSource is Spring's configured pool of those connections; it is the boundary used by both Flyway and the connectivity test.

## 5. Project Structure

```text
backend/
├── pom.xml
├── .env.example
├── README.md
├── scripts/
│   ├── setup-dev-db.sh
│   ├── reset-dev-db.sh
│   └── seed-dev-db.sh
└── src/
    ├── main/
    │   ├── java/vn/edu/medmaintenance/MaintenanceApplication.java
    │   └── resources/application.yml, application-dev.yml
    └── test/java/vn/edu/medmaintenance/BackendFoundationIntegrationTest.java
```

`pom.xml` declares the build and copies migration resources. The main class starts Spring Boot. `application.yml` contains shared Flyway, health and local HTTP settings; `application-dev.yml` supplies the JDBC URL from environment variables. The **authoritative** migration files remain in `database/migrations/`; Maven copies them unfiltered to `target/classes/db/migration/`, without adding a second source set. `README.md` contains setup and troubleshooting commands.

## 6. Maven Configuration

The POM has only Spring Web, Actuator, Spring JDBC, PostgreSQL JDBC, Flyway core plus PostgreSQL support, and the Spring Boot test starter. It has no JPA, Security, Redis, Kafka, JWT, Swagger, Docker or Testcontainers dependency. Java is compiled for release 17. The repository already pins Maven 3.9.11 through `scripts/use-toolchain.sh`, so a second Maven Wrapper was not added.

## 7. Database Connection

The `dev` profile builds `jdbc:postgresql://127.0.0.1:55432/medical_maintenance_backend_dev` from `DB_HOST`, `DB_PORT` and `DB_NAME`. `DB_USERNAME` and `DB_PASSWORD` are required environment variables; no real password is in application files. Spring creates the DataSource, and Flyway uses that same connection.

### Beginner Note — Environment variable and profile

An environment variable is a value supplied by the local shell rather than committed source. A Spring profile selects a named set of settings; `dev` keeps the local JDBC configuration apart from common application settings. This project has only a default/dev setup, not speculative staging or production profiles.

## 8. PostgreSQL Development Setup

Phase 1's private cluster originally used a Unix socket on port 5432 with TCP disabled. Standard PostgreSQL JDBC uses TCP, while Ubuntu's unrelated system cluster already owns TCP port 5432. `backend/scripts/setup-dev-db.sh` verified the project data directory, moved the **same private cluster** to port **55432**, and enabled listening only on `127.0.0.1`. The Phase 1 scripts now use its private Unix socket on 55432.

The script adds one `pg_hba.conf` SCRAM rule for the dedicated database and role `ltnc_backend_dev`; the general loopback reject rule remains. It creates that role without superuser or database-creation rights and makes it owner only of `medical_maintenance_backend_dev`. It generates a local password in ignored `.local-postgres/backend-dev.env` (mode 600). The original `medical_maintenance_db` and its 40 equipment records were checked intact after the change. No system cluster or unrelated database was changed.

## 9. Flyway Integration

Maven packages the original six migrations from `database/migrations/` in version order. A byte-for-byte check of all six JAR resources against their source SQL files passed. Flyway created `public.flyway_schema_history`, applied V001–V006, and recorded six successful SQL rows. Later starts validated their checksums and reported **“No migration necessary.”**

The Phase 1 SQL files already wrap each version in `BEGIN`/`COMMIT`. `spring.flyway.execute-in-transaction=false` avoids a second Flyway transaction around those unchanged files; the SQL files still run their own transactions. `baseline-on-migrate=false`, `validate-on-migrate=true` and `clean-disabled=true` keep the migration path explicit. [Spring Boot lists these Flyway settings](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/); [Flyway documents per-migration transaction handling](https://documentation.red-gate.com/fd/migration-transaction-handling-273973399.html). A process interruption between a script's COMMIT and Flyway's metadata write could require resetting this dedicated dev database; the frozen SQL was not rewritten to remove that boundary.

### Beginner Note — Migration

A migration is a numbered SQL change. Flyway records which numbers completed, so a new database can be built in order and a later startup does not repeat table creation.

## 10. Existing Database Integration Decision

The Phase 1 `medical_maintenance_db` was created by direct SQL before Flyway and contains demo data. Automatic baselining would mark an existing schema as migrated without proving that Flyway created it. The backend instead uses the **separate empty** `medical_maintenance_backend_dev` on the same project-owned cluster. A guarded reset script drops only this backend dev database when a fresh Flyway demonstration is needed. The Phase 1 database remains a comparison baseline.

## 11. Demo Data Handling

Flyway migrations contain schema only. After proving startup and health with **zero business rows**, `backend/scripts/seed-dev-db.sh` loaded the six existing Phase 1.3 SQL seed files separately in one transaction. The backend dev database then held the documented 603 synthetic rows, including 40 devices and 52 plan items. Another startup with this data succeeded without applying migrations again. Demo data is not a Flyway migration and contains no usable login password.

## 12. Application Startup

From the repository root:

```bash
source scripts/use-toolchain.sh
./backend/scripts/setup-dev-db.sh
source .local-postgres/backend-dev.env
cd backend
env -u DEBUG mvn spring-boot:run
```

`env -u DEBUG` suppresses verbose diagnostics caused by an unrelated workstation `DEBUG=release` environment value. A packaged JAR was also built and started on `APP_PORT=18080` during verification. The HTTP server binds to loopback only. The application does not require the demo seed to start.

## 13. Health Check

`GET http://127.0.0.1:18080/actuator/health` returned **HTTP 200** with `{"status":"UP"}` during the packaged-JAR run. It remained UP after loading the synthetic seed and restarting. Only the health endpoint is exposed; details are hidden. [Actuator health documentation](https://docs.spring.io/spring-boot/api/rest/actuator/health.html).

### Beginner Note — Health endpoint

A health endpoint is a small operational check for the running application. `UP` here means the app and its configured database connection are working; it does not mean maintenance business features exist.

## 14. Verification Tests

| Check | Expected | Result |
| --- | --- | --- |
| Java / Maven / PostgreSQL | Java 17, Maven, PostgreSQL 16 | 17.0.20.1 / 3.9.11 / 16.15 |
| Maven build | Runnable JAR | `BUILD SUCCESS` |
| Spring context | Application and DataSource load | PASS |
| PostgreSQL connection | Real server; `SELECT 1 = 1` | PASS |
| Flyway clean start | V001–V006 recorded | 6 successful rows |
| Business schema smoke test | 14 tables, 117 columns, 31 FKs | PASS |
| Packaged-JAR restart | No migration reapplied | “No migration necessary” |
| Health | HTTP 200, `UP` | PASS |
| Integration suite | Four real-DB tests | 4 passed, 0 failed |

The tests use `@SpringBootTest` and a real PostgreSQL DataSource, not a mock or Testcontainers. They cover context, connection, schema inventory and the HTTP health endpoint. They run after sourcing the ignored dev environment file.

## 15. Database Schema Regression

| Measure | Frozen Phase 1 | Flyway backend dev |
| --- | ---: | ---: |
| Business tables | 14 | 14 |
| Business columns | 117 | 117 |
| Business foreign keys | 31 | 31 |
| Flyway metadata tables | 0 | 1, excluded above |

Beyond counts, normalized `pg_dump --schema-only` output of the two databases was **identical** after excluding `flyway_schema_history` and each dump's random `\restrict`/`\unrestrict` token. This compared table definitions, constraints and indexes; no frozen schema file or migration was changed.

## 16. Problems Found

- The private PostgreSQL cluster did not expose a JDBC-compatible TCP endpoint; system TCP port 5432 belongs to a different cluster.
- The populated Phase 1 database had no Flyway history, so starting Flyway against it would be unsafe.
- Frozen migration SQL already contains explicit transactions; Flyway's default wrapping would duplicate transaction ownership.
- The workstation exports `DEBUG=release`, which made the first Spring Boot log unusually verbose.

## 17. Fixes Applied

- Moved only the project-owned cluster to loopback port 55432 and added one database/role-specific SCRAM rule.
- Created a separate backend dev database and non-superuser owner role; left Phase 1 data intact.
- Configured Flyway to consume unmodified SQL through Maven resources, with explicit transaction handling and no automatic baseline.
- Used `env -u DEBUG` in documented run/test commands; no application feature was added to handle the unrelated shell setting.

## 18. Security / Secret Handling

The app and PostgreSQL TCP listener bind only to `127.0.0.1`. The dedicated login role is non-superuser and owns only the backend dev database. The generated credential is in `.local-postgres/backend-dev.env`, ignored by Git and mode 600; source contains only placeholders. `backend/target/`, `.env`, runtime database files and logs are ignored. No Spring Security or authentication feature was implemented.

## 19. What Has NOT Been Implemented Yet

No JPA entities, repositories, DTOs, domain/business services, authentication, RBAC, UC01–UC12 workflow, or frontend were added. Actuator is an operational endpoint, not a business API. The frozen schema is unchanged.

## 20. Phase 2.2 Handoff

Phase 2.2 can use a running Java 17/Spring Boot project, a real authenticated PostgreSQL DataSource, Flyway-managed 14-table schema, optional separate synthetic seed, and a passing connectivity/health test. It can add persistence mappings intentionally, without re-creating tables or enabling Hibernate DDL. Continue to use the frozen data dictionary and contract for each mapping.

## 21. Final Status

**PASS.** The clean database was migrated by Flyway, the application built and started, the health endpoint returned UP, the second startup applied no migration, four integration tests passed, and the backend business schema exactly matched Phase 1. The private Phase 1 database and migration sources remained intact.

## 22. Slide-ready Summary

- Spring Boot 3.5.16 now runs on the project's Java 17 and Maven toolchain.
- An authenticated loopback connection reaches a separate backend dev PostgreSQL database.
- Flyway builds the unchanged 14-table design from six original SQL migrations.
- Demo data stays separate; startup works with zero or 603 synthetic business rows.
- Real database tests and `/actuator/health` confirm connectivity and schema readiness.
- JPA, security and maintenance workflows remain for later phases.
