#!/usr/bin/env bash
set -euo pipefail

DB_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
DB_CLUSTER_DIR="${DB_ROOT}/.local-postgres"
DB_DATA_DIR="${DB_CLUSTER_DIR}/data"
DB_SOCKET_DIR="${DB_CLUSTER_DIR}/run"
DB_NAME='medical_maintenance_db'
DB_PORT='55432'
# A copied cluster may retain the original initdb administrator name.
if [[ -f "${DB_CLUSTER_DIR}/admin.env" ]]; then
    source "${DB_CLUSTER_DIR}/admin.env"
fi
DB_USER="${LTNC_PG_ADMIN:-$(id -un)}"
source "${DB_ROOT}/scripts/use-toolchain.sh"
PG_BIN="${LTNC_PG_BIN}"
export PGHOST="${DB_SOCKET_DIR}" PGPORT="${DB_PORT}" PGUSER="${DB_USER}"

start_local_cluster() {
    mkdir -p "${DB_CLUSTER_DIR}" "${DB_SOCKET_DIR}"
    chmod 700 "${DB_CLUSTER_DIR}" "${DB_SOCKET_DIR}"
    if [[ ! -f "${DB_DATA_DIR}/PG_VERSION" ]]; then
        "${PG_BIN}/initdb" -D "${DB_DATA_DIR}" --auth-local=trust --auth-host=reject --no-instructions
    fi
    if ! "${PG_BIN}/pg_ctl" -D "${DB_DATA_DIR}" status >/dev/null 2>&1; then
        "${PG_BIN}/pg_ctl" -D "${DB_DATA_DIR}" -l "${DB_CLUSTER_DIR}/server.log" \
            -o "-p ${DB_PORT} -c listen_addresses=127.0.0.1 -k ${DB_SOCKET_DIR}" start
    fi
    pg_isready -h "${DB_SOCKET_DIR}" -p "${DB_PORT}" >/dev/null
    actual_data_dir="$(psql -X -At -d postgres -c 'SHOW data_directory')"
    [[ "${actual_data_dir}" == "${DB_DATA_DIR}" ]] || {
        echo 'Refusing to use a PostgreSQL cluster outside this project.' >&2
        exit 1
    }
}

require_dev_confirmation() {
    [[ "${1:-}" == '--yes' ]] || {
        echo "Development-only destructive command. Re-run with --yes to reset ${DB_NAME}." >&2
        exit 2
    }
}
