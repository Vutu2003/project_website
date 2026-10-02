#!/usr/bin/env bash
set +x
set -euo pipefail
PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${PROJECT_ROOT}/scripts/use-toolchain.sh"
if [[ "${1:-}" != '--test-env' ]]; then
    [[ $# == 0 ]] || { echo 'Usage: migrate-dev-db.sh [--test-env]'; exit 2; }
    "${PROJECT_ROOT}/backend/scripts/setup-dev-db.sh"
    source "${PROJECT_ROOT}/.local-postgres/backend-dev.env"
else
    [[ $# == 1 && "${DB_NAME:-}" =~ ^medical_maintenance_[a-z0-9_]+_test$ && "${DB_HOST:-}" == 127.0.0.1 ]] || exit 2
fi
JAR="${PROJECT_ROOT}/backend/target/medical-equipment-maintenance-backend-0.1.0-SNAPSHOT.jar"
if [[ ! -f "${JAR}" ]]; then
    env -u DEBUG mvn -q -f "${PROJECT_ROOT}/backend/pom.xml" -DskipTests package
fi
LIB_DIR="${PROJECT_ROOT}/backend/target/setup-libs"
python3 - "${JAR}" "${LIB_DIR}" <<'PY'
from pathlib import Path
import sys,zipfile
jar,dest=Path(sys.argv[1]),Path(sys.argv[2]);dest.mkdir(exist_ok=True)
with zipfile.ZipFile(jar) as archive:
    for name in archive.namelist():
        if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
            (dest/Path(name).name).write_bytes(archive.read(name))
PY
java -cp "${LIB_DIR}/*" "${PROJECT_ROOT}/backend/scripts/MigrateDatabase.java" "${PROJECT_ROOT}/database/migrations"
