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
  # Run every probe binary under cpp/build/probes/
  for probe in cpp/build/probes/*_probe; do
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
