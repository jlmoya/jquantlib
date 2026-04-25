# Phase 2b — Execution Progress

**Last update:** 2026-04-25
**Tip commit:** `e7d550e` on `origin/main`
**Baseline test suite:** 632 tests, 22 skipped, 0 failures.
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
| L3 Task 3.5 | WI-3 update Vasicek carveout disposition | `9855bc6` | Doc-only. `phase2a-carveouts.md::WI-4-carveout-Vasicek` Disposition block extended with the four fix-commit references (dc02443, 244bc92, 072d25d, 82697d2 + chore 17e2f5b). WI-3 fully closed; L3 done. |
| L4 Task 4.1 | WI-4 SABR investigation | (no commit; diagnosis recorded in `93f2774` progress doc) | Diagnosis: branch (a). Throw fires during `SABRCoeffHolder` construction, BEFORE any LM iteration. Java tests `!Double.isNaN(beta_)` to detect "no guess provided", but the API contract uses `Constants.NULL_REAL = Double.MAX_VALUE`; check never matches; NULL_REAL flows to `validateSabrParameters` which throws on β > 1. Phase-2a carveout hypothesis (transformation drift) was wrong. A4 NOT triggered — fix is local and small. |
| L4 Task 4.2 | WI-4 SABR sentinel fix + un-skip | `e7d550e` | Replaces `!Double.isNaN(X_)` with `X_ != Constants.NULL_REAL` for α/β/ν/ρ in `SABRInterpolation.java::SABRCoeffHolder`. Removes the unconditional `*IsFixed_ = false` block that defeated the constructor's IsFixed args. New `sabr_interpolation_probe` captures C++ post-defaultValues params. Both `SABRInterpolationTest::testSABRInterpolationTest` and `InterpolationTest::testSabrInterpolation` un-skipped (LM and Simplex × 16 IsFixed combos × vegaWeighted both ways at 5e-8). `phase2a-carveouts.md::WI-2-carveout-SABR` Resolution block added. Skipped 24 → 22; suite at 632/0/0/22. WI-4 closed. |

---

## Side effect to track (from L3 Task 3.1 code review)

The CalibratedModel pre-fill silently rescued construction of `BlackKarasinski`, `CoxIngersollRoss`, and `G2` — all three previously threw `IndexOutOfBoundsException` from `arguments_.get(i)` immediately after `super(N)` (because the ArrayList had capacity but size 0). They still have the same field-copy bug Vasicek used to have (assign `private Parameter foo_ = arguments_.get(i)` then immediately overwrite that field with `new ConstantParameter(...)` — Parameter remains stuck at `NullParameter`). Construction now succeeds but parameters remain unbound from `arguments_`. Tasks 3.2 (HullWhite), 3.3 (BlackKarasinski), 3.4 (CoxIngersollRoss) migrate them onto the indirection. G2 is a Phase-2c carveout — its drift will be addressed there.

---

## Code-review followups for Phase 2c

Captured during Phase 2b reviews; not blocking 2b but worth tracking:

- **WI-4 / SABR probe needs a Java consumer.** `migration-harness/references/math/interpolations/sabr_interpolation.json` is currently an orphan reference — the fix's regression coverage rides on the calibration tests (which would also catch a sentinel revert, but indirectly). A small Java test asserting `sabr.beta() / .nu() / .rho()` post-construction equal the JSON values would directly pin the sentinel logic. Skip α in the assertion (Java's α default formula `Math.sqrt(0.2)` differs from C++'s `0.2·F^(1−β)`, also Phase-2c).
- **WI-4 / SABR test 16-IsFixed loop is a silent no-op when guesses are NULL_REAL.** The two un-skipped tests pass `Constants.NULL_REAL` for all four guesses, so even with the `*IsFixed_` flags now wired correctly, every `else` branch fires and the loop runs 16 identical calibrations. Either augment the tests with seeded guesses or add an explanatory comment; the latter is the cheaper move.
- **WI-3 / one-factor model α-default formulas (cross-cutting).** Java's α default in SABR is `√0.2` (constant); C++ is `0.2·F^(1−β)` (forward-aware). Same nature of divergence applies to other one-factor models' default-population paths.
- **WI-1 / HestonProcess `discountBondOption` stub** returns hard-coded 0.0/1.0 — depends on aligning Java's `NonCentralChiSquaredDistribution` (~1.5e-12 drift from C++) before the fix can be cross-validated.

## L4 Task 4.1 — SABR investigation done; diagnosis = branch (a) with twist

The β-out-of-[0,1] failure is in `SABRCoeffHolder`'s constructor (`SABRInterpolation.java:163-183`), NOT in the transformation. The throw fires during construction, before any LM/Simplex iteration. Root cause: Java tests `!Double.isNaN(beta_)` to detect "no guess provided", but the QuantLib API contract uses `Constants.NULL_REAL = Double.MAX_VALUE` for that sentinel. The check never matches, so the four NULL_REAL slots flow through to `validateSabrParameters` which rightly throws (β = MAX_VALUE > 1).

The phase2a-carveouts.md hypothesis ("LM converges to β outside [0,1]") was wrong — optimizer never runs. The line-412 commented `atan` hint in the design doc is also a red herring; the transformation `direct(x) = exp(-x²)` matches C++ identically.

**Branch (a) confirmed.** A4 NOT triggered — fix is ~5-10 LOC inside `SABRInterpolation.java` (replace `!Double.isNaN(x)` with `x != Constants.NULL_REAL` for α, β, ν, ρ; remove the unconditional `*IsFixed_ = false` lines that defeat the constructor's `*IsFixed` arguments). Both SABR tests share the exact same root cause; both unblock together.

Secondary divergences flagged but not gating the throw (track for later if test convergence assertions fail after sentinel fix): α default formula differs (Java=√0.2, C++=0.2·F^(1−β)), missing Halton multi-restart, missing `shift`/`volatilityType`/`errorAccept`/`useMaxError`/`maxGuesses` plumbing.

## Next — L5 (completion doc + tag)

Write `docs/migration/phase2b-completion.md` per `phase2b-design.md` §6 exit criteria. Tag `jquantlib-phase2b-complete` at the final commit, push.

---

## Deviations from the plan to be aware of

1. **L0 Pre-flight done in-session, not via subagent.** The plan describes pre-flight as a `Task 0.1` checkbox sequence but it's verification-only with no commits — dispatching three subagents (implementer + spec reviewer + code quality reviewer) for "run scanner and check baseline tests" is ceremonial. Done directly by the controller; tasks marked complete in TodoWrite.
2. **Scanner stub IDs differ slightly from the plan's pre-flight expectation.** Plan expected `CapHelper#line23` and `G2#generateArguments`; actual scanner output (with the Phase-2a method-name heuristic improvements) names them `CapHelper#Period` and `G2#G2`. Same files, same lines — no material drift. Worth a follow-up sometime to tighten the heuristic for these two.
3. **L1 reviewer hygiene findings folded into a follow-on chore commit** rather than amended onto the original commit, per project rule (no `git commit --amend` for landed work).

---

## Remaining work (from `phase2b-plan.md`)

- L5 — completion doc + tag (next; final task)
- L3 Task 3.2 — HullWhite indirection (A8 risk)
- L3 Task 3.3 — BlackKarasinski indirection (A8 risk)
- L3 Task 3.4 — CoxIngersollRoss indirection (A8 risk)
- L3 Task 3.5 — Update Vasicek carveout disposition
- L4 Task 4.1 — SABR investigation (A4 carve gate)
- L4 Task 4.2 OR 4.3 — SABR fix-or-carve outcome
- L5 Task 5.1 — `phase2b-completion.md`
- L5 Task 5.2 — Tag `jquantlib-phase2b-complete`

Pause-trigger A8 stays armed for the entire L3 sweep. A4 stays armed for the L4 SABR investigation.
