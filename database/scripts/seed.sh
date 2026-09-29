#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
start_local_cluster
existing="$(psql -X -At -d "${DB_NAME}" -c 'SELECT count(*) FROM department')"
[[ "${existing}" == '0' ]] || {
    echo 'Phase 1.3 seed requires an empty freshly migrated database. Use reset_database.sh --yes.' >&2
    exit 1
}
seed_args=()
for seed_file in "${DB_ROOT}"/database/seeds/0[1-6]_*.sql; do
    [[ -f "${seed_file}" ]] || { echo 'Missing ordered seed SQL file.' >&2; exit 1; }
    seed_args+=(-f "${seed_file}")
done
psql -X -q --single-transaction -v ON_ERROR_STOP=1 -d "${DB_NAME}" "${seed_args[@]}"
echo 'Phase 1.3 synthetic demo seed complete.'
