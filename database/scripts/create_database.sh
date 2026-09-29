#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
start_local_cluster
if psql -X -At -d postgres -c "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" | grep -qx 1; then
    echo "Database ${DB_NAME} already exists."
else
    createdb -T template0 -E UTF8 "${DB_NAME}"
    echo "Created ${DB_NAME} on private local socket ${PGHOST}:${PGPORT}."
fi
