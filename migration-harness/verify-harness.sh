#!/usr/bin/env bash
# Regenerate all references in a temp dir; diff against committed.
# Non-zero exit = a probe is non-deterministic or references are stale.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Save current references
cp -R references "$tmp/committed"

# Regenerate in place
./generate-references.sh

# Diff — ignore generated_at timestamp field
diff_output=$(diff -r "$tmp/committed" references || true)
# Strip lines that differ only because of timestamps
filtered=$(echo "$diff_output" | grep -vE '"generated_at"' || true)

# Hard restore: delete the (possibly dirty) references dir and copy the
# snapshot back. `cp -Rf` alone would not remove files that a buggy or
# non-deterministic probe created during the regeneration run.
rm -rf references
cp -R "$tmp/committed" references

if [[ -n "$filtered" ]]; then
  echo "=== FAIL: references drifted beyond timestamp ==="
  echo "$filtered" | head -40
  exit 1
fi

echo "=== OK: harness deterministic, references match ==="
