# Phase 5.3 — Local Development & Multi-Role Test Setup

Runtime naming update: commands and source links below use the current unversioned script names. Historical JSON evidence, hashes and patches retain the names used during their original audits; see the [runtime script rename report](../../version2/runtime_script_rename_report.md) for this later change.

Date: 2026-09-28. Status: **PASS**. Version 1 business/schema remains frozen.

## 1. Objective

Make accepted local V1 easy to start, inspect, stop, log in and test with several roles. This phase adds local shell helpers and usage notes only; no business feature, account-management action, V2, deployment or CI/CD work.

## 2. Starting Baseline

Read accepted Phase 5.1/5.2 startup documentation, V1 integration freeze, existing toolchain/PostgreSQL/login scripts and actual auth/session implementation before writing helpers. Initial source hashes matched the 267-file Phase 5.2 manifest. Current endpoints remain frontend 5173, backend 8080 and private PostgreSQL 55432.

Reviewed integration reports, evidence, helpers and freeze files remain byte-identical. New runtime evidence is [phase_5_3_validation_evidence.json](phase_5_3_validation_evidence.json).

## 3. Scripts Added

| File | Purpose |
| --- | --- |
| [start.sh](../../../scripts/start.sh) | One-command PostgreSQL/backend/frontend readiness and safe launch/reuse |
| [status.sh](../../../scripts/status.sh) | Authenticated PostgreSQL check, backend UP and frontend login readiness |
| [stop.sh](../../../scripts/stop.sh) | Verified helper-owned app shutdown; optional private-cluster shutdown |
| [runtime-common.sh](../../../scripts/runtime-common.sh) | Shared root/identity/port/readiness/launch/stop routines |

A separate restart/window helper was unnecessary; restart is stop then start, and role windows are documented manual profiles/tabs.

## 4. Start Workflow

`./scripts/start.sh` resolves the repository root, selects the existing toolchain, checks prerequisites and acquires an ignored runtime lock. It calls existing `setup-dev-db.sh`, sources the two ignored env files and verifies the expected local DB/ports/origin.

Both app ports are checked for unrelated listeners before launch. Existing healthy project services are reused without claiming their ownership. Missing backend package is built with the existing Maven project; installed Vite CLI is run directly with the same `--host 127.0.0.1` behavior as package.json's dev script so its PID is the actual server.

Detached sessions, closed lock FD, PID/start-time records and logs live under ignored `.local-run/`. Backend must reach health UP; frontend must serve its real login SPA. No passwords/tokens are printed. Frontend launch excludes DB/JWT/demo credentials from its inherited environment; backend launch excludes unneeded demo-password variables.

## 5. Stop Workflow

`./scripts/stop.sh` stops frontend then backend using the recorded PID, Linux start time, actual executable, project working directory and expected app argument. SIGTERM precedes a bounded wait and identity-checked SIGKILL fallback. No broad pkill is used.

Missing/stale/unmatched records are not signaled. Externally launched apps remain untouched and are identified as such. Default stop leaves PostgreSQL running. `--with-db` checks the project cluster directory/port and refuses DB shutdown while app ports are still occupied, then uses pg_ctl fast shutdown on that private cluster only.

## 6. Status Workflow

`./scripts/status.sh` reports OK/DOWN/BLOCKED for the three services and backend health UP. PostgreSQL is checked through the existing authenticated connection, without printing credentials. Unexpected listeners are not mistaken for project services. Nonzero exit status indicates an unavailable/blocked layer.

## 7. Account Inspection

The live join from user_account to department verified these four active users:

| Username | Role | Department |
| --- | --- | --- |
| demo_vtyt | PHONG_VTYT | #2, Phòng Vật tư Y tế |
| demo_bgd | BAN_GIAM_DOC | None |
| demo_khoa_noi | KHOA_PHONG | #1, Khoa Nội |
| demo_admin | ADMIN | #2, Phòng Vật tư Y tế |

The main guide includes exact copy/paste psql connection commands and read-only all-user/demo/active/KHOA queries. No hash, password or account UPDATE/DELETE is shown.

## 8. Demo Password Retrieval Strategy

Current env keys were inspected without printing their values: DEMO_VTYT_PASSWORD, DEMO_BGD_PASSWORD, DEMO_KHOA_PASSWORD and DEMO_ADMIN_PASSWORD. Locally run:

```bash
rg '^export DEMO_(VTYT|BGD|KHOA|ADMIN)_PASSWORD=' .local-postgres/backend-security.env
```

The filter was executed with output captured privately and verified to select only those four keys; output was not saved or printed. Database password_hash fields were confirmed BCrypt. Hashes cannot recover plaintext; the actual generated local demo passwords come from the ignored security env file. JWT_SECRET and DB password are excluded from the documented retrieval command.

## 9. Multi-Role Browser Setup

Guide option A uses named profiles or normal + Incognito. Option B uses fresh tabs/windows, reflecting the actual tab-scoped sessionStorage implementation. Four suggested profiles map to the four verified demo users; credentials are entered manually.

Real Chrome validation observed a fresh same-profile window with empty sessionStorage, simultaneous VTYT/BGD roles surviving separate refreshes, and an opener window initially copying a harmless sessionStorage marker then changing independently. The guide explains initial copied-login behavior and distinguishes shared Incognito profile from separate named profiles. No optional browser launcher, credential URL or password autofill was added.

## 10. Manual Test Example

The guide describes VTYT create/submit → BGD approve → VTYT FREE route/execution/technical PASS → KHOA handover with temporary VTYT co-signer → VTYT report/history. It also describes two-window stale editing and expected 409/reload UX.

These are user instructions only. Phase 5.3 did not rerun business journeys or create smoke business records. Manual tests add persisted records; the runtime helpers do not clean/reset them.

## 11. Logging / Troubleshooting

Logs append to `.local-run/backend.log` and `.local-run/frontend.log`; PostgreSQL setup and optional package build have separate local logs. Guide shows tail -f and Ctrl+C, clarifying that Ctrl+C stops tail rather than a detached server.

Troubleshooting covers occupied ports, PostgreSQL DOWN, backend not UP, frontend/backend connectivity, expired session, status, stop/restart, runtime lock and missing prerequisites. No broad process kill or automatic seed reset is suggested.

## 12. Validation Results

| Check | Result |
| --- | --- |
| Reuse current external project services | PASS; no duplicate launch or ownership claim |
| Stop leaves external services alone | PASS |
| Cleanly stop prior audit servers, then one-command managed start | PASS |
| Managed processes survive launcher exit | PASS |
| Start twice retains exact app PID/start-time records | PASS |
| PostgreSQL connection/backend UP/frontend login | PASS |
| Real VTYT login after initial start and two restarts | PASS |
| Status accurately reports healthy and stopped states | PASS |
| Stop closes 8080/5173; private DB remains | PASS |
| Forged PID record leaves unrelated sleep/Chrome alive | PASS |
| Unrelated listeners on each app port refused and untouched | PASS |
| Start again after stop | PASS |
| Optional --with-db and full stack start again | PASS |
| Account query/password filter/session observations | PASS |
| Canonical DB rows/schema/migrations unchanged | PASS |

The recorded validation finished with **60 successful assertions**, including source/secret/document audits. Final state: all three services running for usability review; managed app PID/start-time records saved in ignored `.local-run/`. No business-data cleanup was necessary. Canonical fingerprints match accepted Phase 5.2: **603 rows, 14 tables, 117 columns, 31 FKs, V001–V006**.

## 13. Regression Result

`bash -n` passed for all four new shell files. Frontend `npm run build --prefix frontend`, lint and test passed: **20 tests across 8 files**, zero failures.

Backend source was unchanged, so full backend business regression was not rerun, as allowed by the request. Backend UP and real login/me were verified after restart. Accepted prior full-suite evidence remains preserved.

## 14. Security / Secret Handling

`.local-run/` is now Git-ignored; directory mode 700 and generated PID/log files mode 600. Secret env files remain ignored and are not copied to runtime files. Runtime PID records contain only PID/start time. Commands disable shell tracing before sourcing credentials. Credentials are supplied through local process environment rather than command-line values.

Recorded stdout, reports, evidence, new helpers and runtime logs were scanned for actual current demo passwords, DB password and signing key. Browser/API capture retains only credential presence/matching booleans, public identity and methods/paths; it does not save raw tokens. No screenshots were created. See final secret/source validation in evidence.

## 15. Files Added / Changed

Added the four shell files in section 3 and these new files under `reports/version1/testing_v1/`:

- [local_v1_runtime_guide.md](local_v1_runtime_guide.md).
- [quick_commands.md](quick_commands.md).
- [phase_5_3_local_runtime_multi_role_setup_report.md](phase_5_3_local_runtime_multi_role_setup_report.md).
- [phase_5_3_validation_evidence.json](phase_5_3_validation_evidence.json).

Changed only `.gitignore` among pre-existing tracked-candidate source files, adding `.local-run/`. No backend/frontend business source, API, role rule, state machine, transaction semantics, schema, migration or seed changed. Existing integration artifacts were not rewritten.

## 16. Known Limitations

Helpers target this Linux/Bash local layout and existing PostgreSQL 16/toolchain/dependencies/security env. Missing backend package can be built; missing frontend dependencies require the documented existing npm install step. This is not deployment/process supervision and does not auto-restart crashed services.

Stop owns only processes it launched. Externally launched servers must be closed from their original terminals. PID files should not be manually removed while those services are running. No browser window helper was added; profile/tab setup is manual. Business data remains persisted, and users must choose reviewed cleanup when they need the canonical fixture after manual writes.

## 17. Final Status

**PASS.** Local start/status/stop/restart, password/account distinction and verified multi-role session instructions are ready for human usability review. Frontend regression passed; canonical data/business source stayed frozen; no Version 2 feature was implemented.
