#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
"${ROOT}/backend/scripts/reset-dev-db.sh" "$@"
"${ROOT}/backend/scripts/setup-v2.sh"
"${ROOT}/database/scripts/validate_demo_data.sh"
