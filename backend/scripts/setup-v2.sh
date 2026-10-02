#!/usr/bin/env bash
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
count="$(psql -X -At -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" -c 'SELECT count(*) FROM department')"
if [[ "${count}" == 0 ]]; then
    "${ROOT}/backend/scripts/seed-dev-db.sh"
else
    echo 'V2 business data already present; no reseed or password reset performed.'
fi
