#!/usr/bin/env bash
# Regenerate all references in a temp dir; diff against committed.
# Non-zero exit = a probe is non-deterministic or references are stale.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

tmp="$(mktemp -d)"
# Restore references/ from the snapshot on ANY exit path — clean, error,
# or mid-run abort under `set -e`. The snapshot may not exist yet (if we
# haven't reached the snapshot step), so guard the restore.
trap '
  if [[ -d "$tmp/committed" ]]; then
    rm -rf references
    cp -R "$tmp/committed" references
  fi
  rm -rf "$tmp"
' EXIT

# Save current references
cp -R references "$tmp/committed"

# Regenerate in place
./generate-references.sh

# Diff — ignore lines containing the generated_at timestamp field.
# diff -I pattern ignores hunks where all changed lines match the pattern,
# which is exactly what we want: a run that only changes generated_at is clean.
# BSD diff (macOS 14+) and GNU diff match on this usage; tested on both.
if ! diff -r -I '"generated_at"' "$tmp/committed" references; then
  echo "=== FAIL: references drifted beyond timestamp ==="
  exit 1
fi

echo "=== OK: harness deterministic, references match ==="
