#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
require_dev_confirmation "${1:-}"
start_local_cluster
dropdb --if-exists --force "${DB_NAME}"
echo "Dropped development database ${DB_NAME}."
