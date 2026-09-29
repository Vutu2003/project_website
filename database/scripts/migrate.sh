#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
start_local_cluster
if ! psql -X -At -d postgres -c "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" | grep -qx 1; then
    echo "Database ${DB_NAME} is absent. Run create_database.sh first." >&2
    exit 1
fi
table_count="$(psql -X -At -d "${DB_NAME}" -c "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p')")"
[[ "${table_count}" == '0' ]] || {
    echo "Migration requires an empty public schema; found ${table_count} tables. Use reset_database.sh --yes for the dedicated development database." >&2
    exit 1
}
for migration in "${DB_ROOT}"/database/migrations/V[0-9]*.sql; do
    [[ -f "${migration}" ]] || { echo 'No migration files found.' >&2; exit 1; }
    echo "APPLY $(basename -- "${migration}")"
    psql -X -q -v ON_ERROR_STOP=1 -d "${DB_NAME}" -f "${migration}"
done
echo 'Migrations complete.'
