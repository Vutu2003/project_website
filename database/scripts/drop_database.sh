#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
require_dev_confirmation "${1:-}"
start_local_cluster
[[ "${DB_NAME}" == medical_maintenance_v2 ]] || exit 1
umask 077
mkdir -p "${DB_CLUSTER_DIR}/backups"
if psql -X -At -d postgres -c "SELECT 1 FROM pg_database WHERE datname='medical_maintenance_v2'" | rg -qx 1; then
    pg_dump -Fc -d "${DB_NAME}" -f "${DB_CLUSTER_DIR}/backups/v2_before_drop_$(date +%Y%m%d_%H%M%S).dump"
    dropdb --force "${DB_NAME}"
fi
