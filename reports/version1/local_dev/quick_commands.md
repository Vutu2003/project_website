# V1 Quick Commands

Run from `/home/vutu0809/Desktop/LTNC`. Detailed explanations: [runtime guide](local_v1_runtime_guide.md).

## Start

```bash
./scripts/start.sh
```

## Open

http://localhost:5173/login

## Status

```bash
./scripts/status.sh
```

Backend health: http://localhost:8080/actuator/health

## Stop

```bash
./scripts/stop.sh
# Optional: also stop the private PostgreSQL cluster.
./scripts/stop.sh --with-db
```

## Restart

```bash
./scripts/stop.sh
./scripts/start.sh
```

## Demo Users

- demo_vtyt — PHONG_VTYT.
- demo_bgd — BAN_GIAM_DOC.
- demo_khoa_noi — KHOA_PHONG.
- demo_admin — ADMIN; no account management.

## Show Current Demo Password Variables

Run locally; keep its output out of reports/screenshots/source:

```bash
rg '^export DEMO_(VTYT|BGD|KHOA|ADMIN)_PASSWORD=' .local-postgres/backend-security.env
```

Only DEMO_VTYT_PASSWORD, DEMO_BGD_PASSWORD, DEMO_KHOA_PASSWORD and DEMO_ADMIN_PASSWORD are selected. Plaintext passwords cannot be recovered from PostgreSQL BCrypt hashes.

## Show Accounts from PostgreSQL

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

## Multi-Role Windows

Use separate profiles (VTYT/BGD/KHOA/ADMIN), or fresh tabs with independent sessionStorage. A copied/opener tab can initially inherit a login; log out there and choose its intended role.

## Logs

```bash
tail -f .local-run/backend.log
tail -f .local-run/frontend.log
```

Ctrl+C stops tail. PostgreSQL address: `127.0.0.1:55432`. Default stop preserves PostgreSQL and all business data.
