# Local V1 Runtime & Testing Guide

Use these helpers for the already configured, frozen local V1. Commands below are run in the repository root. In VS Code, open **Terminal → New Terminal**. If needed, first paste:

```bash
cd /home/vutu0809/Desktop/LTNC
```

The helpers use the existing project toolchain, PostgreSQL setup and local env files. They do not reset data, seed accounts or change business rules. See the [quick commands](quick_commands.md) for a short copy/paste reference.

## 1. Quick Start

Paste:

```bash
./scripts/start.sh
```

Wait for PostgreSQL, Backend and Frontend to show **RUNNING**, then open:

**http://localhost:5173/login**

The command returns to the prompt while the servers keep running in the background. Repeating it reuses healthy project services and avoids duplicate launches. An unrelated app on 8080/5173 is left untouched and reported as a port conflict.

The ignored `.local-run/` folder contains backend/frontend PID records and logs. PID records include the PID and Linux process start time so stop can detect reused/stale PIDs. If the backend package is missing, start builds the existing package through Maven; frontend dependencies and the accepted toolchain must already be installed.

## 2. Check Status

```bash
./scripts/status.sh
```

`[OK]` means the service is reachable; backend also shows **health UP**. `[DOWN]` means it is stopped/unavailable. `[BLOCKED]` means its port belongs to another or unidentified process. Status returns a nonzero exit code when any layer is unavailable, which is useful for scripts.

## 3. Stop

```bash
./scripts/stop.sh
```

This stops only backend/frontend processes launched by `start.sh`. PostgreSQL stays running, and persisted business data stays in the database. Processes started earlier in another terminal are left alone; use **Ctrl+C in their original terminal** to stop them.

To also stop this project's private PostgreSQL cluster:

```bash
./scripts/stop.sh --with-db
```

The optional command verifies the private cluster path and port and refuses DB shutdown while app ports are still occupied. It does not stop a system PostgreSQL service or another project's cluster.

## 4. Restart

```bash
./scripts/stop.sh
./scripts/start.sh
```

To restart all three layers:

```bash
./scripts/stop.sh --with-db
./scripts/start.sh
```

Refresh the browser after RUNNING appears. If your old token has expired, log in again. No separate restart helper is needed.

## 5. Local URLs

| Service | Address |
| --- | --- |
| Frontend | http://localhost:5173 |
| Login | http://localhost:5173/login |
| Backend | http://localhost:8080 |
| Backend health | http://localhost:8080/actuator/health |
| PostgreSQL | 127.0.0.1:55432 |
| Local database | medical_maintenance_backend_dev |

Open the frontend login page for normal use. The backend root URL is an API server; the health URL is its simple readiness check. Use the **localhost** frontend URL so it matches the frozen development CORS origin.

## 6. Demo Accounts

Verified against the current PostgreSQL database; all four are active:

| Username | Role | Meaning |
| --- | --- | --- |
| demo_vtyt | PHONG_VTYT | Phòng Vật tư Y tế: plans, execution, technical acceptance and reports |
| demo_bgd | BAN_GIAM_DOC | Ban Giám đốc: plan/vendor approvals; report/history reads |
| demo_khoa_noi | KHOA_PHONG | Khoa Nội: department-scoped history and handover |
| demo_admin | ADMIN | Technical demo role/dashboard; no account-management feature |

Current department IDs: VTYT/admin = 2 (Phòng Vật tư Y tế); KHOA = 1 (Khoa Nội); BGD has no department. No plaintext password is included here.

## 7. How to Get Current Demo Passwords

Passwords were generated locally and are stored in the ignored, mode-600 file `.local-postgres/backend-security.env`. Run this command **locally** to obtain the current generated demo passwords:

```bash
rg '^export DEMO_(VTYT|BGD|KHOA|ADMIN)_PASSWORD=' .local-postgres/backend-security.env
```

It selects only these four actual variables, excluding JWT_SECRET and DB credentials:

| Username | Local variable |
| --- | --- |
| demo_vtyt | DEMO_VTYT_PASSWORD |
| demo_bgd | DEMO_BGD_PASSWORD |
| demo_khoa_noi | DEMO_KHOA_PASSWORD |
| demo_admin | DEMO_ADMIN_PASSWORD |

Use the password value after `=` in the matching line, without surrounding shell quotes if any are present. Keep the output in your local terminal; do not paste it into reports, chat, screenshots or source files. This guide intentionally includes the command without its output.

If the local security file is missing, startup stops with an explanation. Restore your original local file or follow the existing [backend setup instructions](../../../backend/README.md) deliberately; these runtime helpers do not regenerate passwords or update account hashes.

## 8. View Accounts Directly from PostgreSQL

Start the system/database first. Then copy the entire block:

```bash
source .local-postgres/backend-dev.env
PGPASSWORD="$DB_PASSWORD" psql -X -w \
  -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" \
  -c "SELECT u.id, u.username, u.role_code, u.department_id,
             d.name AS department, u.active
      FROM user_account u
      LEFT JOIN department d ON d.id = u.department_id
      ORDER BY u.id;"
```

This reads account metadata: username, role, department ID/name and active status. PGPASSWORD is supplied through the local environment, not printed or placed in psql's command arguments. No password hash is selected.

For a smaller result, replace the `-c` SQL text above with one of these read-only queries:

```sql
-- The four configured demo logins:
SELECT id, username, role_code, department_id, active
FROM user_account
WHERE username IN ('demo_vtyt','demo_bgd','demo_khoa_noi','demo_admin')
ORDER BY id;

-- Active accounts:
SELECT id, username, role_code, department_id, active
FROM user_account
WHERE active = TRUE
ORDER BY id;

-- KHOA_PHONG accounts and their departments:
SELECT u.id, u.username, u.role_code, u.department_id,
       d.name AS department, u.active
FROM user_account u
JOIN department d ON d.id = u.department_id
WHERE u.role_code = 'KHOA_PHONG'
ORDER BY u.id;
```

No account UPDATE/DELETE commands are part of this setup.

## 9. Why Password Cannot Be Read from Database

`user_account.password_hash` contains **BCrypt hashes**, verified for the four demo logins. BCrypt is a one-way password hash: the backend checks a supplied password against it, but the stored hash does not reveal the original plaintext password. Do not copy `password_hash` into the login form.

Account/role/department/active data comes from PostgreSQL. Current plaintext demo passwords come from the ignored local security env file described in section 7.

## 10. Multi-Role Browser Testing

**Option A — separate browser profiles, or normal + Incognito:** use a clearly named profile/window per role. Four named Chrome profiles make all roles easy to distinguish:

| Window/profile | Login |
| --- | --- |
| VTYT | demo_vtyt |
| BGD | demo_bgd |
| KHOA | demo_khoa_noi |
| ADMIN | demo_admin |

Open `http://localhost:5173/login` in each and enter its credentials manually. A normal Chrome window and an Incognito window are a simple way to compare two roles; add named profiles for more roles. Several Incognito windows share the same private profile, so do not treat each as a separate browser profile.

**Option B — fresh tabs/windows in one profile:** open new tabs and type the login URL, then log in separately. The frontend stores its primary JWT in **sessionStorage**, scoped per tab/window. Live VTYT and BGD windows in the same Chrome profile retained independent roles across refresh.

A window opened by another page with an opener can initially copy the opener's sessionStorage, then changes independently. This was observed using a harmless marker. Duplicating an existing logged-in tab can similarly begin with its role; if a new tab shows the wrong account, click **Đăng xuất** there and log in as the intended user. Refresh keeps the current tab's session. Separate profiles give the clearest visual distinction.

No window helper or password autofill was added. Credentials never belong in browser command-line flags or URLs.

## 11. Suggested Manual Workflow

This is a usage example, not another required full audit. Use a clearly named manual test plan and suitable dates/verified coverage. For the current fixture, equipment DEMO-EQ-001 belongs to Khoa Nội and has FREE coverage.

1. **VTYT:** create and submit the plan.
2. **BGD:** open the approval queue and approve it.
3. **VTYT:** reload the plan, select the verified FREE coverage, start execution, add progress, finish work and record technical PASS.
4. **KHOA:** open the scoped item and record handover PASS. Enter VTYT demo credentials in the temporary co-signer fields; the main KHOA session stays in place.
5. **VTYT:** create/finalize the report and open equipment history. Expected item COMPLETED, plan REPORTED, report FINAL.
6. **ADMIN:** inspect its dashboard and confirm it has no invented business/account-management controls.

Manual business tests add persisted records. Start/stop helpers do not reset or clean those records. Use the existing reviewed cleanup procedure if you later need the canonical 603-row test fixture; do not edit DB statuses to simulate a workflow.

## 12. Optimistic Lock Multi-Window Test

Use two VTYT tabs/profiles and an editable DRAFT or REVISION_REQUIRED plan:

1. Open the same edit form in window A and window B before either saves.
2. In A, change the title and save.
3. In B, save its still-loaded older form without refreshing first.
4. Expect HTTP **409 OPTIMISTIC_LOCK_CONFLICT** and the readable **Xung đột phiên bản** message.
5. Click **Tải lại dữ liệu** in B and inspect the current values before trying again.

Do not change version/status fields directly in PostgreSQL.

## 13. View Backend Logs

```bash
tail -f .local-run/backend.log
```

Press **Ctrl+C** to stop following the log. This stops tail, not the background backend. Logs append across starts and must not intentionally print passwords/JWTs/signing keys. Avoid debug tracing that dumps environment variables.

## 14. View Frontend Logs

```bash
tail -f .local-run/frontend.log
```

Press **Ctrl+C** to stop tail. For PostgreSQL setup problems, inspect `.local-run/postgres-setup.log`; its file is also ignored. A missing-package build uses `.local-run/build.log`.

## 15. Troubleshooting

| Symptom | What to do |
| --- | --- |
| Port 5173 already in use | Run status. If it is a healthy project frontend, start reuses it. Otherwise close the app/original terminal using that port; the helper does not kill it. |
| Port 8080 already in use | Same approach for backend. Do not use broad pkill java/node commands. |
| PostgreSQL DOWN | Run start again; it calls the existing private-cluster setup. Inspect postgres-setup.log if that fails. Do not reset/reseed the database. |
| Backend health not UP | Inspect backend.log, then stop/start the managed services. Existing external servers must be stopped from their own terminal. |
| Frontend cannot reach backend | Check backend health, run status, open the frontend at localhost:5173, and refresh. The default API base is localhost:8080. |
| 401 after an old session | Log out/log in again using the current local demo password. Restarting servers does not extend token lifetime. |
| Another start/stop command is running | Let it finish, then run status or retry. The runtime lock prevents duplicate concurrent launches. |
| Frontend dependencies missing | Run `source scripts/use-toolchain.sh`, then `npm ci --prefix frontend`; retry start. |
| Security env missing | Restore your local security env or consult existing backend setup; helpers will not silently replace passwords. |
| Need a quick reset of running services | Run stop, then start. This restarts services while preserving business data. |

On startup failure, inspect status and the relevant local log, fix the cause, then retry. If an earlier app stage remains running, the stop helper can stop that helper-owned process.

## 16. Important Safety Notes

- Never commit `.local-postgres/` env files or `.local-run/` runtime files.
- Never paste real passwords/JWTs/signing keys/DB passwords into reports, source files or screenshots.
- Use the filtered local password command rather than displaying the whole security env file.
- Do not edit PostgreSQL manually to simulate business transitions or account management.
- These helpers only signal verified helper-owned app processes; do not remove their PID records while servers are running.
- V1 remains frozen. ADMIN account management and the workflow after REPAIR_REQUIRED belong to Version 2.
