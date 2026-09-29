#!/usr/bin/env bash
set +x
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/runtime-common.sh"
[[ $# == 0 ]] || { v1_error 'Usage: ./scripts/status.sh'; exit 2; }
result=0
if v1_postgres_ready; then printf '[OK] PostgreSQL : 127.0.0.1:55432\n'; else printf '[DOWN] PostgreSQL : 127.0.0.1:55432\n'; result=1; fi
for service in backend frontend; do
    port=8080
    [[ "${service}" == 'backend' ]] || port=5173
    pid="$(v1_listener_pid "${port}" || true)"
    if v1_process_matches "${service}" "${pid}"; then
        if [[ "${service}" == 'backend' ]]; then
            health="$(v1_backend_health || true)"
            if [[ "${health}" == 'UP' ]]; then printf '[OK] Backend    : http://localhost:8080 (health UP)\n'; else printf '[DOWN] Backend    : health is not UP\n'; result=1; fi
        elif v1_frontend_ready; then printf '[OK] Frontend   : http://localhost:5173\n'
        else printf '[DOWN] Frontend   : not responding\n'; result=1
        fi
    elif v1_port_open "${port}"; then
        printf '[BLOCKED] %s : port %s belongs to another/unidentified process\n' "${service}" "${port}"
        result=1
    else
        printf '[DOWN] %s : port %s\n' "${service}" "${port}"
        result=1
    fi
done
exit "${result}"
