#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/runtime-common.sh"
[[ $# == 0 || ( $# == 1 && "$1" == '--with-db' ) ]] || { v1_error 'Usage: ./scripts/stop.sh [--with-db]'; exit 2; }
v1_lock
v1_stop_managed frontend
v1_stop_managed backend
for port in 5173 8080; do
    if v1_port_open "${port}"; then printf '[INFO] Port %s still has a process outside this helper; it was left untouched.\n' "${port}"; fi
done
if [[ "${1:-}" == '--with-db' ]]; then
    for port in 5173 8080; do
        if v1_port_open "${port}"; then v1_error 'App ports are still in use. Close those processes before stopping the project database.'; exit 1; fi
    done
    if "${V1_PG_BIN}/pg_ctl" -D "${V1_PG_DATA}" status >/dev/null 2>&1; then
        [[ "$(sed -n '2p' "${V1_PG_DATA}/postmaster.pid")" == "${V1_PG_DATA}" && "$(sed -n '4p' "${V1_PG_DATA}/postmaster.pid")" == '55432' ]] || { v1_error 'Unexpected PostgreSQL cluster identity; refusing to stop it.'; exit 1; }
        "${V1_PG_BIN}/pg_ctl" -D "${V1_PG_DATA}" -m fast -w -t 30 stop
        printf '[OK] Project PostgreSQL: stopped.\n'
    else printf '[INFO] Project PostgreSQL: already stopped.\n'
    fi
else
    printf '[INFO] PostgreSQL kept running. Use --with-db to stop the project cluster too.\n'
fi
