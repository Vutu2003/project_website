#!/usr/bin/env bash
set -euo pipefail

[[ "${1:-}" == '--yes' ]] || {
    echo 'Development-only reset. Re-run with --yes to replace medical_maintenance_backend_dev.' >&2
    exit 2
}
PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${PROJECT_ROOT}/database/scripts/common.sh"
start_local_cluster

# This script targets only the isolated backend development database.
BACKEND_DB_NAME='medical_maintenance_backend_dev'
dropdb --if-exists --force "${BACKEND_DB_NAME}"
createdb -T template0 -E UTF8 -O ltnc_backend_dev "${BACKEND_DB_NAME}"
echo "Reset ${BACKEND_DB_NAME} to an empty PostgreSQL database. Start Spring Boot to apply Flyway migrations."
