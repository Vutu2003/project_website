#!/usr/bin/env bash
# Install the pinned Phase 0 tools locally. No sudo or system configuration.
set -euo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
tools_dir="${root}/.toolchain"

if [[ -x "${tools_dir}/jdk17/bin/java" && -x "${tools_dir}/maven/bin/mvn" && -x "${tools_dir}/node/bin/node" ]]; then
  echo "Project-local toolchain already installed."
  exit 0
fi

if [[ -e "${tools_dir}/jdk17" || -e "${tools_dir}/maven" || -e "${tools_dir}/node" ]]; then
  echo "Incomplete .toolchain detected; inspect it before retrying." >&2
  exit 1
fi

mkdir -p "${tools_dir}"
download_dir="$(mktemp -d "${tools_dir}/.download.XXXXXX")"
trap 'rm -rf -- "$download_dir"' EXIT

curl -fsSL --retry 2 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz' -o "${download_dir}/jdk17.tar.gz"
printf '3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e  %s\n' "${download_dir}/jdk17.tar.gz" | sha256sum -c -

curl -fsSL --retry 2 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.tar.gz' -o "${download_dir}/maven.tar.gz"
printf 'bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978  %s\n' "${download_dir}/maven.tar.gz" | sha512sum -c -

curl -fsSL --retry 2 'https://nodejs.org/dist/v24.21.0/node-v24.21.0-linux-x64.tar.xz' -o "${download_dir}/node.tar.xz"
printf 'fd8e59d5a511510f6a298afb548f18c7d2b1be404d8b4a27d94fbe49f56cb2d6  %s\n' "${download_dir}/node.tar.xz" | sha256sum -c -

mkdir "${tools_dir}/jdk17" "${tools_dir}/maven" "${tools_dir}/node"
tar -xzf "${download_dir}/jdk17.tar.gz" -C "${tools_dir}/jdk17" --strip-components=1
tar -xzf "${download_dir}/maven.tar.gz" -C "${tools_dir}/maven" --strip-components=1
tar -xJf "${download_dir}/node.tar.xz" -C "${tools_dir}/node" --strip-components=1

echo "Toolchain installed. Run: source scripts/use-toolchain.sh"
