#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
require_dev_confirmation "${1:-}"
"${DB_ROOT}/database/scripts/drop_database.sh" --yes
"${DB_ROOT}/database/scripts/create_database.sh"
"${DB_ROOT}/database/scripts/migrate.sh"
"${DB_ROOT}/database/scripts/seed.sh"
"${DB_ROOT}/database/scripts/verify_database.sh"
"${DB_ROOT}/database/scripts/validate_demo_data.sh"
echo 'CLEAN REBUILD PASS.'
