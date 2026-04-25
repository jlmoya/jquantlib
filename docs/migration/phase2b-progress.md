# Phase 2b — Execution Progress

**Last update:** 2026-04-25
**Tip commit:** `244bc92` on `origin/main`
**Baseline test suite:** 630 tests, 24 skipped, 0 failures.
**Scanner:** 2 stubs (2 WIP — CapHelper + G2; both Phase-2c carveouts, unchanged from Phase 2a tip).

---

## Done

| Layer | Task | Commit | Notes |
|---|---|---|---|
| L0 | Pre-flight (baseline green, scanner snapshot, harness verified) | — (no commit) | 626/0/0/25 confirmed; scanner = 2 (CapHelper + G2). |
| L1 | WI-1 HestonProcess `QuadraticExponentialMartingale` | `d0de1e4` | 626 → 628 tests. QEM enum + martingale `k0` correction in both psi sub-branches; probe extended with 2 cases; 2 new Java tests. Spec-reviewed and code-quality-reviewed. |
| L1 chore | HestonProcessTest header refresh for QEM coverage | `29f4dbf` | Picked up the 3 minor doc-hygiene findings from L1 code review. Behavior unchanged. |
| L2 Task 2.2 | WI-2 Simplex 1D fix (test-side) | `f593de6` | Diagnosis A held: `Array.add(double)` is non-mutating. Fix: replace `new Array(0); initialValue.add(-100.0)` with `new Array(new double[] { -100.0 })` aligned to C++ `test-suite/optimizers.cpp:231-232`. OptimizerTest stays `@Ignore`d (un-skip is Task 2.3). Suite unchanged at 628/0/0/25. |
| L2 Task 2.3 | WI-2 un-skip `OptimizerTest` | `aa1a993` | `@Ignore` + carveout-pointer comment + unused `Ignore` import removed; `LevenbergMarquardt` uncommented in the active method matrix. Both Simplex and LM converge to x ≈ −0.5 inside `rootEpsilon=1e-8`. `phase2a-carveouts.md::WI-2-carveout-simplex` disposition updated. Skipped 25 → 24. WI-2 fully closed. |
| L3 Task 3.1 | WI-3 Vasicek arguments_-indirection | `dc02443` | A8 fired at compile time (HullWhite reaches into Vasicek's deleted fields; CalibratedModel had latent IOOB). Resolved by folding two adjacent patches into the same commit: (a) `CalibratedModel(int nArguments)` pre-fills `arguments_` with `NullParameter` slots so `set(i, ...)` works; (b) HullWhite's two `b_=NullParameter()` / `lambda_=NullParameter()` lines patched to `arguments_.set(1/3, NullParameter())`. Vasicek itself: 4 fields deleted, 4 `aParam()/bParam()/sigmaParam()/lambdaParam()` accessors added (originally `private`, broadened to `protected` in fast-follow), scalar accessors routed through them, ctor uses `arguments_.set(...)`. Probe + test added; tight tier passes. 628 → 629 tests. |
| L3 Task 3.1 chore | Broaden Vasicek Param accessors + side-effect note | `17e2f5b` | Promoted `aParam()/bParam()/sigmaParam()/lambdaParam()` from `private` to `protected` so HullWhite (and Tasks 3.3/3.4) can reuse them. Doc note added recording that the CalibratedModel pre-fill silently rescued construction of BK/CIR/G2 (no longer throws IOOB; still has the field-copy bug — to be migrated by Tasks 3.3/3.4; G2 is Phase-2c). |
| L3 Task 3.2 | WI-3 HullWhite arguments_-indirection coverage | `244bc92` | Zero Java source changes — HullWhite's reads were already routing through inherited Vasicek `a()/sigma()` scalar accessors which themselves now hit the indirection. The two-line slot-set patch from Task 3.1 (`arguments_.set(1, NullParameter)`, `set(3, NullParameter)`) was already correct. Probe + Java test added (discountBondOption fingerprint at three (strike, mat, bondMat) tuples against flat 4% curve, `a=0.1, sigma=0.01`). Tight tier passes. 629 → 630 tests. Pre-existing HullWhite drift flagged but out of scope: missing 5-arg `discountBondOption` overload, `convexityBias` formula divergence, `tree(grid)` index-vs-time key mismatch (already marked with `// ?????` in source). |

---

## Side effect to track (from L3 Task 3.1 code review)

The CalibratedModel pre-fill silently rescued construction of `BlackKarasinski`, `CoxIngersollRoss`, and `G2` — all three previously threw `IndexOutOfBoundsException` from `arguments_.get(i)` immediately after `super(N)` (because the ArrayList had capacity but size 0). They still have the same field-copy bug Vasicek used to have (assign `private Parameter foo_ = arguments_.get(i)` then immediately overwrite that field with `new ConstantParameter(...)` — Parameter remains stuck at `NullParameter`). Construction now succeeds but parameters remain unbound from `arguments_`. Tasks 3.2 (HullWhite), 3.3 (BlackKarasinski), 3.4 (CoxIngersollRoss) migrate them onto the indirection. G2 is a Phase-2c carveout — its drift will be addressed there.

---

## Next — L3 Task 3.3 (BlackKarasinski full indirection)

Migrate BlackKarasinski onto the indirection. BK does NOT extend Vasicek (it extends `OneFactorModel` directly), so it owns its own slot indices (0=a, 1=sigma per its 2-parameter ctor). The current Phase-1 code does `this.a_ = arguments_.get(0); this.sigma_ = arguments_.get(1);` then immediately overwrites those fields with `new ConstantParameter(...)` — same field-copy bug Vasicek had. Apply the indirection: delete the two `protected Parameter a_; sigma_;` fields, add `protected Parameter aParam()/sigmaParam()` accessors, route any internal reads, ctor uses `arguments_.set(0/1, ...)`.

BlackKarasinski uses a tree-based discountBondOption (not closed-form), so the fingerprint probe approach used for Vasicek/HullWhite may not be a clean fit. Per the plan, fall back to a reflection-based test that confirms `aParam()`/`sigmaParam()` return the same instance held in `arguments_` if a fingerprint isn't available; the refactor itself is the deliverable for this model.

---

## Deviations from the plan to be aware of

1. **L0 Pre-flight done in-session, not via subagent.** The plan describes pre-flight as a `Task 0.1` checkbox sequence but it's verification-only with no commits — dispatching three subagents (implementer + spec reviewer + code quality reviewer) for "run scanner and check baseline tests" is ceremonial. Done directly by the controller; tasks marked complete in TodoWrite.
2. **Scanner stub IDs differ slightly from the plan's pre-flight expectation.** Plan expected `CapHelper#line23` and `G2#generateArguments`; actual scanner output (with the Phase-2a method-name heuristic improvements) names them `CapHelper#Period` and `G2#G2`. Same files, same lines — no material drift. Worth a follow-up sometime to tighten the heuristic for these two.
3. **L1 reviewer hygiene findings folded into a follow-on chore commit** rather than amended onto the original commit, per project rule (no `git commit --amend` for landed work).

---

## Remaining work (from `phase2b-plan.md`)

- L3 Task 3.3 — BlackKarasinski full indirection (next; A8 still armed)
- L3 Task 3.2 — HullWhite indirection (A8 risk)
- L3 Task 3.3 — BlackKarasinski indirection (A8 risk)
- L3 Task 3.4 — CoxIngersollRoss indirection (A8 risk)
- L3 Task 3.5 — Update Vasicek carveout disposition
- L4 Task 4.1 — SABR investigation (A4 carve gate)
- L4 Task 4.2 OR 4.3 — SABR fix-or-carve outcome
- L5 Task 5.1 — `phase2b-completion.md`
- L5 Task 5.2 — Tag `jquantlib-phase2b-complete`

Pause-trigger A8 stays armed for the entire L3 sweep. A4 stays armed for the L4 SABR investigation.
