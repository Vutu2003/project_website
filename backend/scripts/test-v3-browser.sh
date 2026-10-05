#!/usr/bin/env bash
# Real Google Chrome validation against a disposable canonical V3 database.
set +x
set -euo pipefail
TASK_MODE="${1:-workflow}"
[[ "$#" -le 1 && ( "$TASK_MODE" == workflow || "$TASK_MODE" == --dashboard ) ]] || exit 2
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT}/scripts/use-toolchain.sh"
source "${ROOT}/.local-postgres/backend-security.env"
TASK_CLUSTER="$(mktemp -d /tmp/ltnc-v3-browser.XXXXXX)"
export DB_HOST=127.0.0.1 DB_PORT=55441 DB_NAME=medical_maintenance_v3_browser_test DB_USERNAME="$(id -un)"
export DB_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')"
export APP_PORT=18081 FRONTEND_ORIGIN=http://localhost:15175 VITE_API_BASE_URL=http://localhost:18081
backend_pid= frontend_pid=
cleanup() {
 [[ -z "${backend_pid}" ]] || kill "${backend_pid}" 2>/dev/null || true
 [[ -z "${frontend_pid}" ]] || kill "${frontend_pid}" 2>/dev/null || true
 [[ "${TASK_CLUSTER}" == /tmp/ltnc-v3-browser.* ]] || return 1
 pg_ctl -D "${TASK_CLUSTER}/data" -m fast -w stop >/dev/null 2>&1 || true
 rm -rf -- "${TASK_CLUSTER}"
}
trap cleanup EXIT
initdb -D "${TASK_CLUSTER}/data" --auth-local=trust --auth-host=trust --no-instructions >/dev/null
mkdir "${TASK_CLUSTER}/run"
pg_ctl -D "${TASK_CLUSTER}/data" -l "${TASK_CLUSTER}/postgres.log" -o "-h 127.0.0.1 -p ${DB_PORT} -k ${TASK_CLUSTER}/run" -w start >/dev/null
createdb -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" "$DB_NAME"
"${ROOT}/backend/scripts/migrate-dev-db.sh" --test-env
"${ROOT}/backend/scripts/seed-dev-db.sh" --test-env --baseline v3
if [[ "$TASK_MODE" == --dashboard ]]; then
 psql -X -q -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -f "${ROOT}/database/tests/dashboard_browser_fixture.sql"
fi
env -u DEBUG java -jar "${ROOT}/backend/target/medical-equipment-maintenance-backend-0.1.0-SNAPSHOT.jar" > /tmp/v3-browser-backend.log 2>&1 & backend_pid=$!
(cd "${ROOT}/frontend" && exec node "${ROOT}/frontend/node_modules/vite/bin/vite.js" --host 127.0.0.1 --port 15175) > /tmp/v3-browser-frontend.log 2>&1 & frontend_pid=$!
for attempt in $(seq 1 60); do if curl -fsS http://localhost:18081/actuator/health >/dev/null 2>&1 && curl -fsS http://localhost:15175/login >/dev/null; then break; fi; sleep 1; done
if [[ "$TASK_MODE" == --dashboard ]]; then
 node "${ROOT}/scripts/validate-dashboard-chrome.mjs"
else
 node "${ROOT}/scripts/validate-v3-chrome.mjs"
fi
