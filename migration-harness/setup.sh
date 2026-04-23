#!/usr/bin/env bash
# One-time setup: init submodule, build QuantLib, build all probes.
# Idempotent — safe to re-run.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== 1/3 ensuring QuantLib submodule is at v1.42.1 ==="
git submodule update --init --recursive
(cd cpp/quantlib && git fetch --tags --depth=1 origin v1.42.1 && git checkout v1.42.1)

echo "=== 2/3 configuring and building QuantLib + probes ==="
cmake -S cpp -B cpp/build \
  -DCMAKE_BUILD_TYPE=Release \
  -DQL_BUILD_EXAMPLES=OFF \
  -DQL_BUILD_TEST_SUITE=OFF \
  -DQL_BUILD_BENCHMARK=OFF
cmake --build cpp/build -j

echo "=== 3/3 setup complete ==="
echo "Run ./generate-references.sh to produce reference JSONs."
