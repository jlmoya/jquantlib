#!/usr/bin/env bash
# One-time setup: init submodule, build QuantLib, build all probes.
# Idempotent — safe to re-run.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== 1/3 ensuring QuantLib submodule is at v1.42.1 ==="
git submodule update --init --recursive
# Skip the re-fetch if the tag is already present (common on re-runs).
# Intentionally no --depth=1: shallowing an existing full clone has edge
# cases with older git versions and breaks the initial clone on some
# hosts, for negligible size savings on an already-cloned repo.
(cd cpp/quantlib && \
  git rev-parse --verify v1.42.1 >/dev/null 2>&1 || \
    git fetch --tags origin v1.42.1) && \
(cd cpp/quantlib && git checkout v1.42.1)

echo "=== 2/3 configuring and building QuantLib + probes ==="
cmake -S cpp -B cpp/build \
  -DCMAKE_BUILD_TYPE=Release \
  -DQL_BUILD_EXAMPLES=OFF \
  -DQL_BUILD_TEST_SUITE=OFF \
  -DQL_BUILD_BENCHMARK=OFF
cmake --build cpp/build -j

echo "=== 3/3 setup complete ==="
echo "Run ./generate-references.sh to produce reference JSONs."
