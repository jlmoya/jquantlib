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

if [[ -n "$filtered" ]]; then
  echo "=== FAIL: references drifted beyond timestamp ==="
  echo "$filtered" | head -40
  # Restore committed references so the working tree isn't dirty
  cp -Rf "$tmp/committed"/* references/
  exit 1
fi

# Restore the timestamp-only-changed files too (no real drift)
cp -Rf "$tmp/committed"/* references/
echo "=== OK: harness deterministic, references match ==="
