#!/usr/bin/env bash
# Explicitly authorized local workflow cleanup with a verified backup first.
set +x
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
[[ $# == 1 && "$1" == --yes ]] || {
    echo 'Usage: stop the project, then prepare-v3.sh --yes (deletes ALL current maintenance workflows).' >&2
    exit 2
}
source "${ROOT}/scripts/runtime-common.sh"
if v1_port_open 8080; then
    echo 'Run ./scripts/stop.sh before cleaning workflows.' >&2
    exit 1
fi
source "${ROOT}/database/scripts/common.sh"
start_local_cluster
source "${ROOT}/.local-postgres/backend-dev.env"
[[ "${DB_HOST}" == 127.0.0.1 && "${DB_PORT}" == 55432 && "${DB_NAME}" == medical_maintenance_v2 ]] || exit 1
export PGPASSWORD="${DB_PASSWORD}"
PSQL=(psql -X -q -v ON_ERROR_STOP=1 -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}")
umask 077
mkdir -p "${ROOT}/.local-postgres/backups"
backup="$(mktemp "${ROOT}/.local-postgres/backups/v2_before_v3_$(TZ=Asia/Ho_Chi_Minh date +%Y%m%d_%H%M%S)_XXXXXX.dump")"
pg_dump -Fc -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" -f "${backup}"
pg_restore --list "${backup}" >/dev/null
printf 'Verified backup: %s\n' "${backup}"
"${PSQL[@]}" -f "${ROOT}/database/seeds/prepare_v3.sql"
echo 'V3 starting point ready: all workflows removed, existing catalogs preserved.'
