#!/usr/bin/env bash
# Shared local runtime helpers; sourced by start/status/stop.sh.
set +x
set -euo pipefail
V1_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
source "${V1_ROOT}/scripts/use-toolchain.sh"
V1_RUN="${V1_ROOT}/.local-run"
V1_JAR="${V1_ROOT}/backend/target/medical-equipment-maintenance-backend-0.1.0-SNAPSHOT.jar"
V1_VITE="${V1_ROOT}/frontend/node_modules/vite/bin/vite.js"
V1_JAVA="${JAVA_HOME}/bin/java"
V1_NODE="${V1_ROOT}/.toolchain/node/bin/node"
V1_PG_BIN="${LTNC_PG_BIN}"
V1_PG_DATA="${V1_ROOT}/.local-postgres/data"
V1_DB_ENV="${V1_ROOT}/.local-postgres/backend-dev.env"
V1_SECURITY_ENV="${V1_ROOT}/.local-postgres/backend-security.env"

v1_error() { printf '[ERROR] %s\n' "$*" >&2; }
v1_runtime_dir() {
    umask 077
    [[ ! -L "${V1_RUN}" ]] || { v1_error '.local-run must be a real project directory.'; return 1; }
    mkdir -p -- "${V1_RUN}"
    chmod 700 "${V1_RUN}"
}
v1_lock() {
    v1_runtime_dir
    exec 9>"${V1_RUN}/control.lock"
    flock -n 9 || { v1_error 'Another start/stop command is running. Wait and try again.'; return 1; }
}
v1_port_open() { [[ -n "$(ss -H -ltn "sport = :$1")" ]]; }
v1_listener_pid() {
    local line
    line="$(ss -H -ltnp "sport = :$1")"
    [[ "${line}" =~ pid=([0-9]+) ]] || return 1
    printf '%s\n' "${BASH_REMATCH[1]}"
}
v1_proc_info() {
    local pid="$1" stat_line
    [[ "${pid}" =~ ^[1-9][0-9]*$ && -r "/proc/${pid}/stat" ]] || return 1
    stat_line="$(<"/proc/${pid}/stat")"
    read -r -a V1_PROC_FIELDS <<< "${stat_line##*) }"
    [[ "${V1_PROC_FIELDS[0]}" != 'Z' ]] || return 1
    V1_PROC_START="${V1_PROC_FIELDS[19]}"
}
v1_process_matches() {
    local kind="$1" pid="$2" cwd executable target
    local -a args=()
    v1_proc_info "${pid}" || return 1
    cwd="$(readlink -f -- "/proc/${pid}/cwd")" || return 1
    executable="$(readlink -f -- "/proc/${pid}/exe")" || return 1
    mapfile -d '' -t args < "/proc/${pid}/cmdline" || return 1
    if [[ "${kind}" == 'backend' ]]; then
        [[ "${cwd}" == "${V1_ROOT}" && "${executable}" == "$(readlink -f -- "${V1_JAVA}")" && "${args[1]:-}" == '-jar' ]] || return 1
        target="${args[2]:-}"
        [[ "${target}" == /* ]] || target="${cwd}/${target}"
        [[ "$(readlink -f -- "${target}")" == "${V1_JAR}" ]]
    else
        [[ "${cwd}" == "${V1_ROOT}/frontend" && "${executable}" == "$(readlink -f -- "${V1_NODE}")" ]] || return 1
        target="${args[1]:-}"
        [[ "${target}" == /* ]] || target="${cwd}/${target}"
        [[ "$(readlink -f -- "${target}")" == "${V1_VITE}" ]]
    fi
}
v1_managed_pid() {
    local kind="$1" pid recorded_start extra
    [[ -f "${V1_RUN}/${kind}.pid" ]] || return 1
    read -r pid recorded_start extra < "${V1_RUN}/${kind}.pid" || return 1
    [[ -z "${extra:-}" && "${recorded_start}" =~ ^[0-9]+$ ]] || return 1
    v1_process_matches "${kind}" "${pid}" || return 1
    [[ "${V1_PROC_START}" == "${recorded_start}" ]] || return 1
    printf '%s\n' "${pid}"
}
v1_backend_health() {
    local body
    body="$(curl -fsS --connect-timeout 1 --max-time 2 http://localhost:8080/actuator/health 2>/dev/null)" || return 1
    printf '%s' "${body}" | python3 -c 'import json,sys; s=json.load(sys.stdin)["status"]; print(s); sys.exit(0 if s=="UP" else 1)' 2>/dev/null
}
v1_frontend_ready() {
    curl -fsS --connect-timeout 1 --max-time 2 http://localhost:5173/login 2>/dev/null | rg -q 'src="/src/main\.tsx([?][^"]*)?"'
}
v1_postgres_ready() {
    [[ -f "${V1_DB_ENV}" ]] || return 1
    "${V1_PG_BIN}/pg_ctl" -D "${V1_PG_DATA}" status >/dev/null 2>&1 || return 1
    (
        source "${V1_DB_ENV}"
        [[ "${DB_HOST}" == '127.0.0.1' && "${DB_PORT}" == '55432' && "${DB_NAME}" == 'medical_maintenance_backend_dev' ]] || exit 1
        PGPASSWORD="${DB_PASSWORD}" psql -X -At -w -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" -c 'SELECT 1' 2>/dev/null | rg -qx '1'
    )
}
v1_launch() {
    local kind="$1" cwd="$2"
    shift 2
    # A separate session plus closed lock FD keeps the service alive after the
    # launcher exits. PID and Linux start time prevent signaling a reused PID.
    (
        cd -- "${cwd}"
        nohup setsid bash -c '
            stat_line=$(<"/proc/$$/stat")
            read -r -a fields <<< "${stat_line##*) }"
            printf "%s %s\n" "$$" "${fields[19]}" > "$1"
            shift
            exec "$@"
        ' _ "${V1_RUN}/${kind}.pid" "$@" 9>&- >>"${V1_RUN}/${kind}.log" 2>&1 < /dev/null &
    )
    for ((attempt=0; attempt<50; attempt++)); do
        if v1_managed_pid "${kind}" >/dev/null 2>&1; then return 0; fi
        sleep 0.1
    done
    v1_error "Could not launch ${kind}. See .local-run/${kind}.log."
    return 1
}
v1_wait_ready() {
    local kind="$1" checks="$2"
    for ((attempt=0; attempt<checks; attempt++)); do
        if [[ "${kind}" == 'backend' ]]; then
            if v1_backend_health >/dev/null; then return 0; fi
        elif v1_frontend_ready; then return 0
        fi
        v1_managed_pid "${kind}" >/dev/null || { v1_error "${kind} exited. See .local-run/${kind}.log."; return 1; }
        sleep 0.5
    done
    v1_error "${kind} did not become ready. See .local-run/${kind}.log."
    return 1
}
v1_stop_managed() {
    local kind="$1" pid recorded_start extra
    if [[ ! -f "${V1_RUN}/${kind}.pid" ]]; then
        printf '[INFO] %s: no helper-owned process recorded.\n' "${kind}"
        return 0
    fi
    pid="$(v1_managed_pid "${kind}")" || {
        printf '[INFO] %s: stale/unmatched PID record; no process signaled.\n' "${kind}"
        rm -f -- "${V1_RUN}/${kind}.pid"
        return 0
    }
    read -r _ recorded_start extra < "${V1_RUN}/${kind}.pid"
    kill -TERM "${pid}" 2>/dev/null || true
    for ((attempt=0; attempt<40; attempt++)); do
        if ! v1_proc_info "${pid}" || [[ "${V1_PROC_START}" != "${recorded_start}" ]]; then
            rm -f -- "${V1_RUN}/${kind}.pid"
            printf '[OK] %s: stopped.\n' "${kind}"
            return 0
        fi
        sleep 0.25
    done
    # Revalidate identity immediately before the final signal.
    if [[ "$(v1_managed_pid "${kind}" || true)" == "${pid}" ]]; then
        kill -KILL "${pid}" 2>/dev/null || true
    fi
    rm -f -- "${V1_RUN}/${kind}.pid"
    printf '[OK] %s: stopped after SIGKILL fallback.\n' "${kind}"
}
