#!/usr/bin/env bash
set -euo pipefail

[[ "${1:-}" == '--yes' ]] || {
    echo 'Development-only reset. Re-run with --yes to replace medical_maintenance_v2.' >&2
    exit 2
}
PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${PROJECT_ROOT}/database/scripts/common.sh"
start_local_cluster

# This script targets only the isolated backend development database.
BACKEND_DB_NAME='medical_maintenance_v2'
umask 077
mkdir -p "${DB_CLUSTER_DIR}/backups"
if psql -X -At -d postgres -c "SELECT 1 FROM pg_database WHERE datname='${BACKEND_DB_NAME}'" | rg -qx 1; then
    pg_dump -Fc -d "${BACKEND_DB_NAME}" -f "${DB_CLUSTER_DIR}/backups/v2_before_reset_$(date +%Y%m%d_%H%M%S).dump"
fi
dropdb --if-exists --force "${BACKEND_DB_NAME}"
createdb -T template0 -E UTF8 -O ltnc_backend_dev "${BACKEND_DB_NAME}"
echo "Reset ${BACKEND_DB_NAME} to an empty PostgreSQL database. Start Spring Boot to apply Flyway migrations."
