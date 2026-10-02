#!/usr/bin/env bash
# Compatibility entry point for canonical local V2 accounts. Credentials stay in ignored env.
set +x
set -euo pipefail
PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${PROJECT_ROOT}/scripts/use-toolchain.sh"
SECURITY_ENV="${PROJECT_ROOT}/.local-postgres/backend-security.env"
umask 077
python3 - "${SECURITY_ENV}" <<'PY'
import base64,os,pathlib,secrets,shlex,sys
path=pathlib.Path(sys.argv[1]);values={}
if path.exists():
    for line in path.read_text().splitlines():
        if line.startswith('export ') and '=' in line:
            key,value=line[7:].split('=',1);values[key]=shlex.split(value)[0]
# Rebuilding account IDs into a new database must invalidate tokens from the old database.
if values.get('LOCAL_DATABASE_ID') != 'medical_maintenance_v2':
    values['JWT_SECRET']=base64.b64encode(secrets.token_bytes(48)).decode()
    values['LOCAL_DATABASE_ID']='medical_maintenance_v2'
values.setdefault('JWT_SECRET',base64.b64encode(secrets.token_bytes(48)).decode())
values.setdefault('JWT_EXPIRATION_SECONDS','3600')
for key in ['LOCAL_VTYT_PASSWORD','LOCAL_BGD_PASSWORD','LOCAL_KHOA_NOI_PASSWORD','LOCAL_KHOA_NGOAI_PASSWORD']:
    values.setdefault(key,secrets.token_urlsafe(18))
# Explicit local administrator credential requested for this V2 environment.
values.setdefault('LOCAL_ADMIN_PASSWORD','admin')
for key in list(values):
    if key.startswith('DEMO_'):del values[key]
path.write_text(''.join(f'export {key}={shlex.quote(value)}\n' for key,value in values.items()))
path.chmod(0o600)
PY
[[ "${1:-}" == '--env-only' && $# == 1 ]] && { echo 'Canonical local security env ready (mode 600).'; exit 0; }
[[ $# == 0 ]] || { echo 'Usage: setup-demo-login.sh [--env-only]'; exit 2; }
source "${PROJECT_ROOT}/.local-postgres/backend-dev.env"
source "${SECURITY_ENV}"
[[ "${DB_NAME}" == medical_maintenance_v2 && "${DB_HOST}" == 127.0.0.1 && "${DB_PORT}" == 55432 ]] || exit 1
python3 "${PROJECT_ROOT}/database/seeds/local_credentials.py" --update
