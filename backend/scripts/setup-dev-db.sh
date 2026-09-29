#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${PROJECT_ROOT}/database/scripts/common.sh"

BACKEND_DB_NAME='medical_maintenance_backend_dev'
BACKEND_DB_USER='ltnc_backend_dev'
BACKEND_ENV_FILE="${DB_CLUSTER_DIR}/backend-dev.env"
HBA_FILE="${DB_DATA_DIR}/pg_hba.conf"

[[ "${DB_DATA_DIR}" == "${PROJECT_ROOT}/.local-postgres/data" ]] || exit 1
mkdir -p "${DB_CLUSTER_DIR}" "${DB_SOCKET_DIR}"
chmod 700 "${DB_CLUSTER_DIR}" "${DB_SOCKET_DIR}"

if [[ ! -f "${DB_DATA_DIR}/PG_VERSION" ]]; then
    "${PG_BIN}/initdb" -D "${DB_DATA_DIR}" --auth-local=trust --auth-host=reject --no-instructions >/dev/null
fi
[[ "$(cat "${DB_DATA_DIR}/PG_VERSION")" == '16' ]] || {
    echo 'The private cluster is not PostgreSQL 16.' >&2
    exit 1
}

# The private cluster initially used socket-only port 5432. Move that same
# project-owned cluster to 55432 for authenticated loopback JDBC access.
if "${PG_BIN}/pg_ctl" -D "${DB_DATA_DIR}" status >/dev/null 2>&1; then
    CURRENT_PORT="$(sed -n '4p' "${DB_DATA_DIR}/postmaster.pid")"
    if [[ "${CURRENT_PORT}" != "${DB_PORT}" ]]; then
        "${PG_BIN}/pg_ctl" -D "${DB_DATA_DIR}" -m fast stop >/dev/null
    fi
fi

python3 - "${HBA_FILE}" "${BACKEND_DB_NAME}" "${BACKEND_DB_USER}" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
database = sys.argv[2]
user = sys.argv[3]
text = path.read_text()
needle = 'host    all             all             127.0.0.1/32            reject'
if needle not in text:
    raise SystemExit('Expected private-cluster host reject rule was not found; refusing to broaden access.')
rule = f'# LTNC BACKEND DEV SCRAM RULE\nhost    {database}    {user}    127.0.0.1/32    scram-sha-256\n'
pattern = r'(?m)^# LTNC BACKEND DEV SCRAM RULE\nhost[^\n]*\n'
if re.search(pattern, text):
    text = re.sub(pattern, rule, text, count=1)
else:
    text = text.replace(needle, rule + needle, 1)
path.write_text(text)
PY

start_local_cluster
"${PG_BIN}/pg_ctl" -D "${DB_DATA_DIR}" reload >/dev/null

umask 077
if [[ ! -f "${BACKEND_ENV_FILE}" ]]; then
    SECRET="$(python3 -c 'import secrets; print(secrets.token_urlsafe(36))')"
    {
        printf 'export DB_HOST=127.0.0.1\n'
        printf 'export DB_PORT=%s\n' "${DB_PORT}"
        printf 'export DB_NAME=%s\n' "${BACKEND_DB_NAME}"
        printf 'export DB_USERNAME=%s\n' "${BACKEND_DB_USER}"
        printf 'export DB_PASSWORD=%s\n' "${SECRET}"
        printf 'export SPRING_PROFILES_ACTIVE=dev\n'
    } > "${BACKEND_ENV_FILE}"
    unset SECRET
fi
chmod 600 "${BACKEND_ENV_FILE}"
source "${BACKEND_ENV_FILE}"
[[ "${DB_NAME}" == "${BACKEND_DB_NAME}" && "${DB_USERNAME}" == "${BACKEND_DB_USER}" ]] || {
    echo 'Unexpected backend environment file; refusing to use it.' >&2
    exit 1
}

if ! psql -X -At -d postgres -c "SELECT 1 FROM pg_roles WHERE rolname = '${BACKEND_DB_USER}'" | grep -qx 1; then
    printf 'CREATE ROLE "%s" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;\n' "${BACKEND_DB_USER}" |
        psql -X -q -v ON_ERROR_STOP=1 -d postgres >/dev/null
fi
# Supply the password on standard input, never in a process argument or log.
printf 'ALTER ROLE "%s" PASSWORD '\''%s'\'';\n' "${BACKEND_DB_USER}" "${DB_PASSWORD}" |
    psql -X -q -v ON_ERROR_STOP=1 -d postgres >/dev/null

if ! psql -X -At -d postgres -c "SELECT 1 FROM pg_database WHERE datname = '${BACKEND_DB_NAME}'" | grep -qx 1; then
    createdb -T template0 -E UTF8 -O "${BACKEND_DB_USER}" "${BACKEND_DB_NAME}"
else
    printf 'ALTER DATABASE "%s" OWNER TO "%s";\n' "${BACKEND_DB_NAME}" "${BACKEND_DB_USER}" |
        psql -X -q -v ON_ERROR_STOP=1 -d postgres >/dev/null
fi

PGPASSWORD="${DB_PASSWORD}" psql -X -At -h "${DB_HOST}" -p "${DB_PORT}" \
    -U "${DB_USERNAME}" -d "${DB_NAME}" -c 'SELECT current_database()' | grep -qx "${BACKEND_DB_NAME}"
echo "Backend dev database ready on 127.0.0.1:${DB_PORT}; credentials: ${BACKEND_ENV_FILE} (mode 600)."
