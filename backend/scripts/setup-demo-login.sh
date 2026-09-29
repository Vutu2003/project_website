#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
DB_ENV="${PROJECT_ROOT}/.local-postgres/backend-dev.env"
SECURITY_ENV="${PROJECT_ROOT}/.local-postgres/backend-security.env"
[[ -f "${DB_ENV}" ]] || { echo 'Run setup-dev-db.sh first.' >&2; exit 1; }
source "${DB_ENV}"
[[ "${DB_NAME}" == 'medical_maintenance_backend_dev' && "${DB_HOST}" == '127.0.0.1' &&
   "${DB_PORT}" == '55432' && "${DB_USERNAME}" == 'ltnc_backend_dev' ]] || {
    echo 'Refusing any database other than the isolated backend dev database.' >&2; exit 1;
}

umask 077
if [[ ! -f "${SECURITY_ENV}" ]]; then
    python3 - "${SECURITY_ENV}" <<'PY'
import base64, pathlib, secrets, shlex, sys
path = pathlib.Path(sys.argv[1])
values = {
    'JWT_SECRET': base64.b64encode(secrets.token_bytes(48)).decode(),
    'JWT_EXPIRATION_SECONDS': '3600',
    'DEMO_VTYT_PASSWORD': secrets.token_urlsafe(24),
    'DEMO_BGD_PASSWORD': secrets.token_urlsafe(24),
    'DEMO_KHOA_PASSWORD': secrets.token_urlsafe(24),
    'DEMO_ADMIN_PASSWORD': secrets.token_urlsafe(24),
}
path.write_text(''.join(f'export {key}={shlex.quote(value)}\n' for key, value in values.items()))
path.chmod(0o600)
PY
fi
chmod 600 "${SECURITY_ENV}"
[[ "${1:-}" == '--env-only' ]] && { echo "Created local security environment: ${SECURITY_ENV}"; exit 0; }
[[ $# == 0 ]] || { echo 'Usage: setup-demo-login.sh [--env-only]' >&2; exit 2; }
source "${SECURITY_ENV}"
python3 -c 'import bcrypt' >/dev/null 2>&1 || {
    echo 'Python bcrypt is required for demo hash setup (install python3-bcrypt).' >&2
    exit 1
}
export PGPASSWORD="${DB_PASSWORD}"
PSQL=(psql -X -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" -v ON_ERROR_STOP=1)
[[ "$("${PSQL[@]}" -At -c 'SELECT current_database()')" == 'medical_maintenance_backend_dev' ]] || exit 1
[[ "$("${PSQL[@]}" -At -c 'SELECT count(*) FROM flyway_schema_history WHERE success=true')" == 6 ]] || {
    echo 'Apply six Flyway migrations first.' >&2; exit 1;
}
python3 - <<'PY' | "${PSQL[@]}" -q >/dev/null
import bcrypt, os
accounts = [
    ('demo_vtyt', 'PHONG_VTYT', 'DEMO_VTYT_PASSWORD'),
    ('demo_bgd', 'BAN_GIAM_DOC', 'DEMO_BGD_PASSWORD'),
    ('demo_khoa_noi', 'KHOA_PHONG', 'DEMO_KHOA_PASSWORD'),
    ('demo_admin', 'ADMIN', 'DEMO_ADMIN_PASSWORD'),
]
print('BEGIN;')
print("DO $$ BEGIN IF (SELECT count(*) FROM user_account WHERE (username,role_code) IN (('demo_vtyt','PHONG_VTYT'),('demo_bgd','BAN_GIAM_DOC'),('demo_khoa_noi','KHOA_PHONG'),('demo_admin','ADMIN')) AND active) <> 4 THEN RAISE EXCEPTION 'Expected four active synthetic demo users'; END IF; END $$;")
for username, role, env in accounts:
    digest = bcrypt.hashpw(os.environ[env].encode(), bcrypt.gensalt(rounds=12)).decode()
    print(f"UPDATE user_account SET password_hash='{digest}' WHERE username='{username}' AND role_code='{role}' AND active;")
print('COMMIT;')
PY
echo "Configured four BCrypt demo logins in ${DB_NAME}; local credentials: ${SECURITY_ENV} (mode 600)."
