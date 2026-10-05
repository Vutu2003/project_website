#!/usr/bin/env bash
# Non-destructive setup: never delete plans when invoked again.
set +x
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
[[ $# == 0 ]] || exit 2
"${ROOT}/backend/scripts/setup-dev-db.sh"
"${ROOT}/backend/scripts/setup-demo-login.sh" --env-only
"${ROOT}/backend/scripts/migrate-dev-db.sh"
source "${ROOT}/scripts/use-toolchain.sh"
source "${ROOT}/.local-postgres/backend-dev.env"
export PGPASSWORD="${DB_PASSWORD}"
PSQL=(psql -X -q -v ON_ERROR_STOP=1 -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}")
count="$("${PSQL[@]}" -At -c 'SELECT count(*) FROM department')"
if [[ "${count}" == 0 ]]; then
    "${ROOT}/backend/scripts/seed-dev-db.sh" --baseline v3
else
    "${PSQL[@]}" --single-transaction -f "${ROOT}/database/seeds/v3_catalog.sql" -f "${ROOT}/database/seeds/v3_automation.sql" \
    -f "${ROOT}/database/seeds/v3_quarters.sql"
    echo 'V3 catalog ready; existing accounts and workflows preserved.'
fi
