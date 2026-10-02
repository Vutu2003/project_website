#!/usr/bin/env bash
# Full regression uses a disposable cluster and the historical assertion fixtures.
set +x
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT}/scripts/use-toolchain.sh"
source "${ROOT}/.local-postgres/backend-security.env"
export DEMO_ADMIN_PASSWORD="${LOCAL_ADMIN_PASSWORD}" DEMO_VTYT_PASSWORD="${LOCAL_VTYT_PASSWORD}" DEMO_BGD_PASSWORD="${LOCAL_BGD_PASSWORD}" DEMO_KHOA_PASSWORD="${LOCAL_KHOA_NOI_PASSWORD}"
TASK_CLUSTER="$(mktemp -d /tmp/ltnc-v2-regression.XXXXXX)"
export DB_HOST=127.0.0.1 DB_NAME=medical_maintenance_v2_regression_test DB_USERNAME="$(id -un)"
export DB_PORT="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')"
export DB_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')"
unset FRONTEND_ORIGIN
cleanup() {
    [[ "${TASK_CLUSTER}" == /tmp/ltnc-v2-regression.* ]] || return 1
    if pg_ctl -D "${TASK_CLUSTER}/data" status >/dev/null 2>&1; then
        dropdb -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" --if-exists "${DB_NAME}"
        pg_ctl -D "${TASK_CLUSTER}/data" -m fast -w stop >/dev/null
    fi
    rm -rf -- "${TASK_CLUSTER}"
}
trap cleanup EXIT
initdb -D "${TASK_CLUSTER}/data" --auth-local=trust --auth-host=trust --no-instructions >/dev/null
mkdir "${TASK_CLUSTER}/run"
pg_ctl -D "${TASK_CLUSTER}/data" -l "${TASK_CLUSTER}/server.log" -o "-h 127.0.0.1 -p ${DB_PORT} -k ${TASK_CLUSTER}/run" -w start >/dev/null
createdb -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" "${DB_NAME}"
"${ROOT}/backend/scripts/migrate-dev-db.sh" --test-env
PSQL=(psql -X -q -v ON_ERROR_STOP=1 -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}")
args=()
for f in "${ROOT}"/database/tests/fixtures/0[1-6]_*.sql; do args+=(-f "${f}"); done
"${PSQL[@]}" --single-transaction "${args[@]}"
python3 - <<'PY' | "${PSQL[@]}" >/dev/null
import bcrypt,os
print('BEGIN;')
for user,key in [('demo_admin','ADMIN'),('demo_vtyt','VTYT'),('demo_bgd','BGD'),('demo_khoa_noi','KHOA')]:
    digest=bcrypt.hashpw(os.environ['DEMO_'+key+'_PASSWORD'].encode(),bcrypt.gensalt(rounds=12)).decode()
    print(f"UPDATE user_account SET password_hash='{digest}' WHERE username='{user}';")
print('COMMIT;')
PY
# Additional arguments may narrow tests; the default always runs the full required suite.
env -u DEBUG mvn -f "${ROOT}/backend/pom.xml" test "$@"
