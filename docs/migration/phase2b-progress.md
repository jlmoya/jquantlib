# Phase 2b — Execution Progress

**Last update:** 2026-04-25
**Tip commit:** `d0de1e4` on `origin/main`
**Baseline test suite:** 628 tests, 25 skipped, 0 failures.
**Scanner:** 2 stubs (2 WIP — CapHelper + G2; both Phase-2c carveouts, unchanged from Phase 2a tip).

---

## Done

| Layer | Task | Commit | Notes |
|---|---|---|---|
| L0 | Pre-flight (baseline green, scanner snapshot, harness verified) | — (no commit) | 626/0/0/25 confirmed; scanner = 2 (CapHelper + G2). |
| L1 | WI-1 HestonProcess `QuadraticExponentialMartingale` | `d0de1e4` | 626 → 628 tests. QEM enum + martingale `k0` correction in both psi sub-branches; probe extended with 2 cases; 2 new Java tests. Spec-reviewed and code-quality-reviewed (3 minor doc-hygiene findings — header refresh below). |

---

## In flight

L1 doc-hygiene chore (HestonProcessTest header + Javadoc updated to mention QEM) — separate `chore(processes)` commit landing alongside this progress snapshot.

---

## Next — L2 Task 2.1+2.2 (Simplex 1D-dim fix)

See `docs/migration/phase2b-plan.md` §Layer 2. Approximate scope:
- Reproduce `OptimizerTest#testOptimizers` failure (`Independent variable must be 1 dimensional`) and identify whether the bug is in `Array.add(double)`, `Simplex.minimize`, or `Constraint.update`.
- Apply the smallest fix that resolves it (≤ 20 LOC expected).
- Commit, then Task 2.3 un-skips `OptimizerTest` and uncomments the LM entry in line 104.

---

## Deviations from the plan to be aware of

1. **L0 Pre-flight done in-session, not via subagent.** The plan describes pre-flight as a `Task 0.1` checkbox sequence but it's verification-only with no commits — dispatching three subagents (implementer + spec reviewer + code quality reviewer) for "run scanner and check baseline tests" is ceremonial. Done directly by the controller; tasks marked complete in TodoWrite.
2. **Scanner stub IDs differ slightly from the plan's pre-flight expectation.** Plan expected `CapHelper#line23` and `G2#generateArguments`; actual scanner output (with the Phase-2a method-name heuristic improvements) names them `CapHelper#Period` and `G2#G2`. Same files, same lines — no material drift. Worth a follow-up sometime to tighten the heuristic for these two.
3. **L1 reviewer hygiene findings folded into a follow-on chore commit** rather than amended onto the original commit, per project rule (no `git commit --amend` for landed work).

---

## Remaining work (from `phase2b-plan.md`)

- L2 Task 2.1 + 2.2 — Simplex fix (next)
- L2 Task 2.3 — Un-skip `OptimizerTest`
- L3 Task 3.1 — Vasicek + indirection pattern (A8 risk)
- L3 Task 3.2 — HullWhite indirection (A8 risk)
- L3 Task 3.3 — BlackKarasinski indirection (A8 risk)
- L3 Task 3.4 — CoxIngersollRoss indirection (A8 risk)
- L3 Task 3.5 — Update Vasicek carveout disposition
- L4 Task 4.1 — SABR investigation (A4 carve gate)
- L4 Task 4.2 OR 4.3 — SABR fix-or-carve outcome
- L5 Task 5.1 — `phase2b-completion.md`
- L5 Task 5.2 — Tag `jquantlib-phase2b-complete`

Pause-trigger A8 stays armed for the entire L3 sweep. A4 stays armed for the L4 SABR investigation.
