#!/usr/bin/env bash
# Build probes (incrementally) and run one or all of them, emitting JSON to references/.
# Usage: ./generate-references.sh [probe_name]   (no arg = run all)

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ ! -d cpp/build ]]; then
  echo "cpp/build missing; run ./setup.sh first" >&2
  exit 1
fi

cmake --build cpp/build -j

if [[ $# -eq 0 ]]; then
  # Collect probe binaries with nullglob so an empty match yields an empty
  # array rather than the literal glob string.
  shopt -s nullglob
  probes=(cpp/build/probes/*_probe)
  shopt -u nullglob
  if (( ${#probes[@]} == 0 )); then
    echo "no probe binaries found in cpp/build/probes/; run ./setup.sh first" >&2
    exit 1
  fi
  for probe in "${probes[@]}"; do
    [[ -x "$probe" ]] || continue
    echo "=== running $(basename "$probe") ==="
    "$probe"
  done
else
  probe="cpp/build/probes/$1"
  [[ -x "$probe" ]] || { echo "no such probe: $1" >&2; exit 1; }
  echo "=== running $1 ==="
  "$probe"
fi

echo "=== generation complete; see references/ for output ==="
