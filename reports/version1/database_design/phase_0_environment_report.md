# Phase 0 — Environment Setup & Project Bootstrap

## 1. Objective

Prepare and verify the Ubuntu development environment and a clean repository skeleton. Database design and application implementation are outside Phase 0.

## 2. Initial Repository State

- Project root and initial working directory: `/home/vutu0809/Desktop/LTNC`.
- Initial contents: `docs/Biểu mẫu.pdf` only. No hidden configuration, Git repository, or application code.
- Initial document SHA-256: `4e012312cfe460320edbed8debb62874d810e2e2e9be813dc73dcc5ad51495a2`.

## 3. Environment Audit

| Component | Detected Version / State | Required / Target | Status | Action |
| --- | --- | --- | --- | --- |
| Ubuntu | 24.04.5 LTS, x86_64, kernel 7.0.0-34-generic | Ubuntu | PASS | None |
| Java runtime | OpenJDK 21.0.12.1 | Java 17 | INCOMPATIBLE | Select project-local Java 17 |
| Java compiler | Missing | Java 17 compiler | MISSING | Install project-local JDK 17 |
| Maven | Missing | Maven using Java 17 | MISSING | Install project-local Maven |
| Git | 2.43.0 | Git | PASS | Initialize repository |
| Node.js | Missing | Supported LTS compatible with Vite | MISSING | Install project-local Node 24 |
| npm | Missing | npm with Node LTS | MISSING | Supplied with Node 24 |
| PostgreSQL client/server | 16.15; cluster `16/main` online | Local PostgreSQL development environment | PASS | None |
| Docker / Compose | Missing | Optional | OPTIONAL | Leave uninstalled; local PostgreSQL suffices |
| curl / unzip | curl 8.5.0 / unzip 6.00 | Basic utilities | PASS | None |

Ubuntu's available `nodejs` package was 18.19.1, so it was not selected. [Node.js lists v24 as LTS](https://nodejs.org/en/about/previous-releases), and [Vite's compatibility note](https://vite.dev/guide/) requires Node 20.19+ or 22.12+.

## 4. Changes Performed

- Downloaded Eclipse Temurin JDK 17.0.20.1, Apache Maven 3.9.11, and Node.js 24.21.0 into ignored `.toolchain/`. Verified the JDK SHA-256 `3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e`, Maven's published SHA-512, and Node's published `SHASUMS256.txt` before extraction. Source locations: [Adoptium release](https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.20.1%2B1), [Maven binary](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/), [Node v24.21.0](https://nodejs.org/dist/v24.21.0/).
- Added `scripts/setup-toolchain.sh` with pinned URLs and checksums for a fresh checkout. Added `scripts/use-toolchain.sh` to set `JAVA_HOME` and place the project-local executables first in `PATH`. Run the setup script once, then source the selection script in each new Bash shell.
- Initialized Git without creating a commit or remote.
- Created the requested root configuration, README, placeholders, and this report. No packages, services, or system Java alternatives were changed.
- During final verification, the source PDF unexpectedly appeared as `docs/temple.pdf`. Its checksum matched the original; its original name `docs/Biểu mẫu.pdf` was restored immediately. The cause of the transient rename was not identified.

## 5. Final Development Environment

| Component | Exact final version / location |
| --- | --- |
| OS | Ubuntu 24.04.5 LTS, x86_64, kernel 7.0.0-34-generic |
| `JAVA_HOME` | `/home/vutu0809/Desktop/LTNC/.toolchain/jdk17` |
| `java` | `.toolchain/jdk17/bin/java`, Temurin 17.0.20.1+1 |
| `javac` | `.toolchain/jdk17/bin/javac`, 17.0.20.1 |
| Maven | `.toolchain/maven/bin/mvn`, 3.9.11 using Temurin 17.0.20.1 |
| Git | 2.43.0 |
| Node.js | `.toolchain/node/bin/node`, v24.21.0 LTS |
| npm | `.toolchain/node/bin/npm`, 11.19.0 |
| PostgreSQL | Client and server 16.15; `16/main` online on port 5432 |
| Docker / Compose | Not installed; optional for this local setup |

The system's unmodified default `java` is 21.0.12.1. The project selects Java 17 through the script above.

## 6. Project Structure

```text
project-root/
├── .editorconfig
├── .gitignore
├── .git/                         # initialized; no commits
├── .toolchain/                   # local binaries; ignored by Git
├── README.md
├── backend/README.md
├── database_design/README.md
├── docs/Biểu mẫu.pdf             # original source file
├── frontend/README.md
├── reports/phase_0_environment_report.md
├── scripts/setup-toolchain.sh
└── scripts/use-toolchain.sh
```

## 7. Verification

Executed after `source scripts/use-toolchain.sh`:

| Command | Concise result |
| --- | --- |
| `java -version` | OpenJDK 17.0.20.1 |
| `javac -version` | 17.0.20.1 |
| `mvn -version` | Maven 3.9.11; Java 17.0.20.1 |
| `git --version` | 2.43.0 |
| `node --version` | v24.21.0 |
| `npm --version` | 11.19.0 |
| `psql --version` | PostgreSQL 16.15 |
| `pg_lsclusters` / `pg_isready` | `16/main` online; port 5432 accepting connections |
| `find docs ... sha256sum` | Original filename and SHA-256 match the initial record |
| `git status --short --branch` | Healthy new repository; intended files untracked |
| `git check-ignore .toolchain/jdk17/bin/java` | Local toolchain ignored |

No database schema, migration, backend application, or frontend application was generated. The final file listing contains only the original PDF and Phase 0 files.

## 8. Audit Findings

- Initial default Java was 21 without `javac`; Maven, Node, and npm were absent.
- Ubuntu's Node 18 package was unsuitable for the selected modern Vite development target.
- Docker and Compose are absent. The existing local PostgreSQL installation makes them optional for Phase 0.
- The source PDF experienced a transient filename change during verification; content remained identical and the original filename was restored.
- No other unexpected initial files or hidden configuration were found.

## 9. Fixes Applied

- Installed and checksum-verified the required binaries locally, leaving system packages and services unchanged.
- Added a shell helper selecting Java 17 for Maven and other project commands.
- Restored the original PDF filename and verified its checksum.
- Initialized Git and added a stack-appropriate `.gitignore` and `.editorconfig`.

## 10. Final Status

PASS — all required Phase 0 tools and repository artifacts are available and verified. PostgreSQL is running; Docker is explicitly optional. The project-local toolchain must be selected in each shell.

## 11. Evidence

```text
$ java -version
openjdk version "17.0.20.1" 2026-08-18
$ javac -version
javac 17.0.20.1
$ mvn -version
Apache Maven 3.9.11
Java version: 17.0.20.1, vendor: Eclipse Adoptium
$ node --version
v24.21.0
$ npm --version
11.19.0
$ psql --version
psql (PostgreSQL) 16.15
$ pg_isready
/var/run/postgresql:5432 - accepting connections
$ sha256sum 'docs/Biểu mẫu.pdf'
4e012312cfe460320edbed8debb62874d810e2e2e9be813dc73dcc5ad51495a2  docs/Biểu mẫu.pdf
```

## 12. Remaining Issues

None for Phase 0. Docker is intentionally optional, and the project-local toolchain is ignored by Git, so a fresh checkout needs `bash scripts/setup-toolchain.sh` before sourcing the helper.

## 13. Slide-ready Summary

- Ubuntu 24.04.5 LTS development host audited.
- Java 17.0.20.1 and Maven 3.9.11 installed locally and verified together.
- Node.js 24.21.0 LTS and npm 11.19.0 verified for future Vite work.
- PostgreSQL 16.15 cluster online and accepting connections.
- Docker left optional because local PostgreSQL is available.
- Git initialized and clean Phase 0 repository structure created.
- Original business PDF restored to its initial path with matching checksum.
- No schema, backend application, or frontend application created.
