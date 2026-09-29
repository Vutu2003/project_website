#!/usr/bin/env bash
# Source this file in Bash to select the project-local Phase 0 toolchain.

_ltnc_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${_ltnc_root}/.toolchain/jdk17"
# Prefer the project-local PostgreSQL/curl packages when installed.
export LTNC_PG_BIN="${_ltnc_root}/.toolchain/runtime/usr/lib/postgresql/16/bin"
if [[ ! -x "${LTNC_PG_BIN}/postgres" ]]; then
  export LTNC_PG_BIN="/usr/lib/postgresql/16/bin"
fi
export PATH="${_ltnc_root}/.toolchain/jdk17/bin:${_ltnc_root}/.toolchain/maven/bin:${_ltnc_root}/.toolchain/node/bin:${LTNC_PG_BIN}:${_ltnc_root}/.toolchain/runtime/bin:${PATH}"
unset _ltnc_root
