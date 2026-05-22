# Claude Code bootstrap — JQuantLib migration

## What this repo is

JQuantLib is a Java port of the C++ [QuantLib](https://github.com/lballabio/QuantLib) quantitative finance library. The Java port began ~2007, stalled ~2021, and is being revived as a multi-phase C++ → Java migration.

## Read this first, every session

**`docs/migration/phase1-design.md`** is the binding design spec for the current phase. Section 12 (decision log) summarizes every operational decision. Sections 4 (workflow), 6 (git), and 7 (quality gates) define how work is executed.

Nothing in this file overrides the design doc. When this file and the design doc disagree, the design doc wins.

## Ground-truth principle

**C++ QuantLib v1.42.1 is source of truth.** The existing Java code is starting material, not a design to preserve. Where Java diverges from v1.42.1 (signatures, implementations, constants, behavior), Java updates to match C++.

Pin: `v1.42.1` @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5` (2026-04-16).

## Current state

- **Phase:** 1 — "finish started stubs" in the existing 61 `org.jquantlib.*` packages.
- **Branch:** `migration/phase1-finish-stubs` (off `main`).
- **Design:** approved 2026-04-22, committed.
- **Plan:** `docs/migration/phase1-plan.md` — generated or in-progress (check if file exists).
- **Harness:** `migration-harness/` — C++ reference infrastructure, scaffolded per design §5.
- **Scanner artifacts:** `docs/migration/stub-inventory.json` + `docs/migration/worklist.md` — ground truth for where work stands.

Phase 2 (filling gaps beyond the existing 61 packages) is explicitly deferred; not designed yet.

## Operational rules (binding)

- **Push direct to `main` per layer** (fast-forward, no squash). No PRs. Solo single-owner repo.
- **No `Co-authored-by: Claude` trailer.** `-s` Signed-off-by trailer yes. Unsigned commits (no GPG/SSH).
- **One stub (or cycle-batch) = one commit.** Every commit compiles and passes `mvn -pl jquantlib test`.
- **TDD + cross-validation.** Every stub fix has a test whose expected value came from running C++ v1.42.1 via `migration-harness/` probes. Never loosen tolerance to force green.
- **Tolerance tiers:** exact (bit-exact) / tight `1e-12` rel `1e-14` abs near zero / loose `1e-8` rel. Per-test exceptions require inline justification.
- **Divergence found mid-stub** → separate preceding `align(<package>): ...` commit, not folded into the stub commit.
- **API changes** to match v1.42.1 are automatic; no per-change approval needed.

## When to pause and ask the user

Default: autonomous work. Pause only for these triggers (full list in design §7.3):

| Trigger | Condition |
|---------|-----------|
| A1 | Scanner stub count > 1000 |
| A2 | Tolerance looser than `1e-8` needed |
| A3 | Cross-validation suggests v1.42.1 itself is wrong |
| A4 | Stub strictly needs a new class outside the 61 existing packages |
| A6 | End of every layer — report summary, wait for acknowledgment |

## Environment gotchas

- **Two `gh` accounts on this machine.** `jlmoya` owns the repo; `Jose-Moya_ETSUN` is the default. Run `gh auth switch -u jlmoya` before any `gh` call that touches `jlmoya/jquantlib`.
- **Remote URL is SSH** (`git@github.com:jlmoya/jquantlib.git`) — HTTPS prompts for interactive password in non-TTY shells.
- **Maven scoping:** prefer `mvn -pl jquantlib test` (inner `jquantlib` module), not `mvn test` at the repo root (pulls in `jquantlib-contrib`, `-helpers`, `-samples`).
- **C++ clone location:** `/Users/josemoya/Projects/GitProjects/QuantLib`. The `migration-harness/cpp/quantlib/` submodule is independent; don't conflate them.
- **`maven-central` branch on origin** is a release-staging branch — do not touch.

## Quick resume checklist for a fresh session

1. `git status`, `git branch --show-current` — confirm on `migration/phase1-finish-stubs`.
2. Read `docs/migration/phase1-design.md` §12 (decision log) and §2 (goals/non-goals).
3. If `docs/migration/phase1-plan.md` exists, read it; the current layer/task is there.
4. `cat docs/migration/worklist.md` — see what's open.
5. `mvn -pl jquantlib test` — confirm a known-green baseline before changing anything.
