# Phase 2b — Execution Progress

**Last update:** 2026-04-25
**Tip commit:** `9855bc6` on `origin/main`
**Baseline test suite:** 632 tests, 24 skipped, 0 failures.
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
| L3 Task 3.3 | WI-3 BlackKarasinski arguments_-indirection | `072d25d` | BK extends OneFactorModel (NOT Vasicek), so it owns its own slots (0=a, 1=sigma). Same pattern as Vasicek: 2 fields deleted, 2 `protected Parameter aParam()/sigmaParam()` accessors added, scalar `a()/sigma()` route through indirection, ctor uses `arguments_.set(0/1, ...)`. Reflection-based Java test (BK has no closed-form pricing — its tree-based `dynamics()` requires the still-stubbed `numericTree`, which is Phase-2c material). Test asserts `aParam()`/`sigmaParam()` return live `arguments_[i]` instances and reflect ctor's a/sigma. 630 → 631 tests. |
| L3 Task 3.4 | WI-3 CoxIngersollRoss arguments_-indirection | `82697d2` | CIR extends OneFactorAffineModel; 4 slots (0=theta, 1=k, 2=sigma, 3=r0 per C++ v1.42.1 init list). Same pattern: 4 fields deleted, 4 protected param accessors added, scalar accessors routed, ctor uses `arguments_.set(0..3, ...)`. Bonus align-fix: sigma constraint corrected from Java's prior `VolatilityConstraint(k, theta)` (too strict) → `PositiveConstraint()` matching C++'s `withFellerConstraint=false` default. Fingerprint test pivoted to `discountBond` (closed-form A·exp(−B·r0)) rather than `discountBondOption` because the latter depends on Java's `NonCentralChiSquaredDistribution`, which has a separate ~1.5e-12 latent drift from C++ — deferred to Phase 2c. 631 → 632 tests. |
| L3 Task 3.5 | WI-3 update Vasicek carveout disposition | (this commit) | Doc-only. `phase2a-carveouts.md::WI-4-carveout-Vasicek` Disposition block extended with the four fix-commit references (dc02443, 244bc92, 072d25d, 82697d2 + chore 17e2f5b). WI-3 fully closed; L3 done. |

---

## Side effect to track (from L3 Task 3.1 code review)

The CalibratedModel pre-fill silently rescued construction of `BlackKarasinski`, `CoxIngersollRoss`, and `G2` — all three previously threw `IndexOutOfBoundsException` from `arguments_.get(i)` immediately after `super(N)` (because the ArrayList had capacity but size 0). They still have the same field-copy bug Vasicek used to have (assign `private Parameter foo_ = arguments_.get(i)` then immediately overwrite that field with `new ConstantParameter(...)` — Parameter remains stuck at `NullParameter`). Construction now succeeds but parameters remain unbound from `arguments_`. Tasks 3.2 (HullWhite), 3.3 (BlackKarasinski), 3.4 (CoxIngersollRoss) migrate them onto the indirection. G2 is a Phase-2c carveout — its drift will be addressed there.

---

## L4 Task 4.1 — SABR investigation done; diagnosis = branch (a) with twist

The β-out-of-[0,1] failure is in `SABRCoeffHolder`'s constructor (`SABRInterpolation.java:163-183`), NOT in the transformation. The throw fires during construction, before any LM/Simplex iteration. Root cause: Java tests `!Double.isNaN(beta_)` to detect "no guess provided", but the QuantLib API contract uses `Constants.NULL_REAL = Double.MAX_VALUE` for that sentinel. The check never matches, so the four NULL_REAL slots flow through to `validateSabrParameters` which rightly throws (β = MAX_VALUE > 1).

The phase2a-carveouts.md hypothesis ("LM converges to β outside [0,1]") was wrong — optimizer never runs. The line-412 commented `atan` hint in the design doc is also a red herring; the transformation `direct(x) = exp(-x²)` matches C++ identically.

**Branch (a) confirmed.** A4 NOT triggered — fix is ~5-10 LOC inside `SABRInterpolation.java` (replace `!Double.isNaN(x)` with `x != Constants.NULL_REAL` for α, β, ν, ρ; remove the unconditional `*IsFixed_ = false` lines that defeat the constructor's `*IsFixed` arguments). Both SABR tests share the exact same root cause; both unblock together.

Secondary divergences flagged but not gating the throw (track for later if test convergence assertions fail after sentinel fix): α default formula differs (Java=√0.2, C++=0.2·F^(1−β)), missing Halton multi-restart, missing `shift`/`volatilityType`/`errorAccept`/`useMaxError`/`maxGuesses` plumbing.

## Next — L4 Task 4.2 (SABR sentinel fix, branch (a))

Apply the sentinel fix in `SABRInterpolation.java::SABRCoeffHolder` ctor; ensure both gated tests un-skip and pass; if convergence assertions fail after the throw goes away, decide whether to land secondary alignments in this commit or carve them as Phase-2c residual.

---

## Deviations from the plan to be aware of

1. **L0 Pre-flight done in-session, not via subagent.** The plan describes pre-flight as a `Task 0.1` checkbox sequence but it's verification-only with no commits — dispatching three subagents (implementer + spec reviewer + code quality reviewer) for "run scanner and check baseline tests" is ceremonial. Done directly by the controller; tasks marked complete in TodoWrite.
2. **Scanner stub IDs differ slightly from the plan's pre-flight expectation.** Plan expected `CapHelper#line23` and `G2#generateArguments`; actual scanner output (with the Phase-2a method-name heuristic improvements) names them `CapHelper#Period` and `G2#G2`. Same files, same lines — no material drift. Worth a follow-up sometime to tighten the heuristic for these two.
3. **L1 reviewer hygiene findings folded into a follow-on chore commit** rather than amended onto the original commit, per project rule (no `git commit --amend` for landed work).

---

## Remaining work (from `phase2b-plan.md`)

- L4 Task 4.2 — SABR sentinel fix (next; branch (a) per Task 4.1 diagnosis)
- L3 Task 3.2 — HullWhite indirection (A8 risk)
- L3 Task 3.3 — BlackKarasinski indirection (A8 risk)
- L3 Task 3.4 — CoxIngersollRoss indirection (A8 risk)
- L3 Task 3.5 — Update Vasicek carveout disposition
- L4 Task 4.1 — SABR investigation (A4 carve gate)
- L4 Task 4.2 OR 4.3 — SABR fix-or-carve outcome
- L5 Task 5.1 — `phase2b-completion.md`
- L5 Task 5.2 — Tag `jquantlib-phase2b-complete`

Pause-trigger A8 stays armed for the entire L3 sweep. A4 stays armed for the L4 SABR investigation.
