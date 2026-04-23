# Stub Scanner

Finds open stubs in the Java tree and emits:
- `docs/migration/stub-inventory.json` — structured list per design §3.2
- `docs/migration/worklist.md` — human-readable checklist by package

## Run

```
python3 tools/stub-scanner/scan_stubs.py
```

Deterministic: given the same Java tree, always produces identical output.
Safe to re-run after each stub resolution.

## What it finds

Five patterns, per `docs/migration/phase1-design.md` §2.3. The current
implementation handles `work_in_progress`, `not_implemented`, and
`numerical_suspect` automatically. `todo_stub` and `fixme_defect` require
manual curation; the scanner does NOT emit them on its own.

## Limitations

- Line-based regex, not a full Java AST. Assumes JQuantLib's current
  formatting conventions. If the codebase is reformatted heavily, re-check
  the match logic.
- Does not (yet) populate `depends_on` / `cpp_counterpart` / `cpp_tests` —
  those are filled during per-stub work.
