#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/runtime-common.sh"
[[ $# == 0 ]] || { v1_error 'Usage: ./scripts/start.sh'; exit 2; }
for tool in curl ss rg flock setsid python3 psql nohup; do
    command -v "${tool}" >/dev/null || { v1_error "Missing local tool: ${tool}"; exit 1; }
done
[[ -x "${V1_JAVA}" && -x "${V1_NODE}" ]] || { v1_error 'Project toolchain is missing. See scripts/setup-toolchain.sh.'; exit 1; }
[[ -f "${V1_SECURITY_ENV}" ]] || { v1_error 'Missing ignored backend-security.env. Restore your local file using the existing backend setup instructions; this helper does not reset demo passwords.'; exit 1; }
[[ -f "${V1_VITE}" ]] || { v1_error 'Frontend dependencies are missing. Run npm ci --prefix frontend with the project toolchain.'; exit 1; }
v1_lock
"${V1_ROOT}/backend/scripts/setup-dev-db.sh" 9>&- >"${V1_RUN}/postgres-setup.log" 2>&1 || { v1_error 'PostgreSQL setup failed. See .local-run/postgres-setup.log.'; exit 1; }
source "${V1_DB_ENV}"
source "${V1_SECURITY_ENV}"
[[ "${DB_HOST}" == '127.0.0.1' && "${DB_PORT}" == '55432' && "${DB_NAME}" == 'medical_maintenance_backend_dev' ]] || { v1_error 'Unexpected database environment; use the project local database.'; exit 1; }
[[ "${APP_PORT:-8080}" == '8080' && "${FRONTEND_ORIGIN:-http://localhost:5173}" == 'http://localhost:5173' ]] || { v1_error 'Local helper requires backend port 8080 and frontend origin http://localhost:5173.'; exit 1; }
v1_postgres_ready || { v1_error 'PostgreSQL connection failed.'; exit 1; }
# Refuse unrelated listeners on either app port before launching an app.
for entry in backend:8080 frontend:5173; do
    service="${entry%:*}"
    port="${entry#*:}"
    if v1_port_open "${port}"; then
        pid="$(v1_listener_pid "${port}" || true)"
        v1_process_matches "${service}" "${pid}" || { v1_error "Port ${port} is used by another process. It was left untouched."; exit 1; }
    fi
done
for service in backend frontend; do
    port=8080
    [[ "${service}" == 'backend' ]] || port=5173
    if v1_port_open "${port}"; then
        pid="$(v1_listener_pid "${port}" || true)"
        v1_process_matches "${service}" "${pid}" || { v1_error "Port ${port} is used by another process. It was left untouched. Close it or see the runtime guide."; exit 1; }
        if [[ "${service}" == 'backend' ]]; then
            v1_backend_health >/dev/null || { v1_error 'Existing backend health is not UP. Check its original terminal/logs.'; exit 1; }
        else
            v1_frontend_ready || { v1_error 'Existing frontend is not responding correctly.'; exit 1; }
        fi
        if ! v1_managed_pid "${service}" >/dev/null; then
            printf '[INFO] Existing %s reused; stop it from its original terminal.\n' "${service}"
        fi
        continue
    fi
    if v1_managed_pid "${service}" >/dev/null; then
        v1_wait_ready "${service}" 120
        continue
    fi
    if [[ "${service}" == 'backend' ]]; then
        if [[ ! -f "${V1_JAR}" ]]; then
            printf '[INFO] Building existing backend package; see .local-run/build.log.\n'
            env -u DEBUG mvn -f "${V1_ROOT}/backend/pom.xml" -DskipTests package >"${V1_RUN}/build.log" 2>&1 || { v1_error 'Backend packaging failed. See .local-run/build.log.'; exit 1; }
        fi
        v1_launch backend "${V1_ROOT}" env -u DEBUG -u DEMO_VTYT_PASSWORD -u DEMO_BGD_PASSWORD -u DEMO_KHOA_PASSWORD -u DEMO_ADMIN_PASSWORD "${V1_JAVA}" -jar "${V1_JAR}"
        v1_wait_ready backend 120
    else
        # Existing package.json dev command is Vite --host 127.0.0.1. Execute
        # that same installed CLI directly so the stored PID is the actual server.
        v1_launch frontend "${V1_ROOT}/frontend" env -u DB_PASSWORD -u DB_USERNAME -u DB_HOST -u DB_PORT -u DB_NAME -u JWT_SECRET -u JWT_EXPIRATION_SECONDS -u DEMO_VTYT_PASSWORD -u DEMO_BGD_PASSWORD -u DEMO_KHOA_PASSWORD -u DEMO_ADMIN_PASSWORD "${V1_NODE}" "${V1_VITE}" --host 127.0.0.1
        v1_wait_ready frontend 60
    fi
done
printf '\nPostgreSQL : RUNNING — 127.0.0.1:55432\nBackend    : RUNNING — http://localhost:8080\nFrontend   : RUNNING — http://localhost:5173\n\nLogin:\nhttp://localhost:5173/login\n'
