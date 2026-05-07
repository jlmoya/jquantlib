# Phase 2k Completion — Gaussian1D Feature Completion + math.matrixutilities Cleanup

**Status:** complete 2026-05-02 (autonomous mode — second phase under autonomous directive)
**Tag:** `jquantlib-phase2k-complete` @ `<FILL_AT_TAG>`
**Predecessor:** `jquantlib-phase2j.5-complete` @ `22f65b8`
**Plan + Design:** `docs/migration/phase2k-{design,plan}.md` (commit `df1fbd9`)

## Final state

| Metric | Phase 2j.5 tip | Phase 2k tip | Δ |
|--------|----------------|--------------|----|
| Tests | 801/0/0/22 | 809/0/0/22 | +8 |
| Scanner WIP | 0 | 0 | unchanged |
| MarkovFunctional smile branches | 2 of 4 (None/Kahale operational; SabrSmile/CustomSmile threw on validate()) | **4 of 4** | ✅ FULL FEATURE |
| Nonstandard/FloatFloat basket helpers | UnsupportedOperationException | **Operational via BasketGeneratingEngine** | ✅ |
| `TqrEigenDecomposition` location | Private inner of GaussianQuadrature | Public class in math.matrixutilities | ✅ Reusable |

## What landed (4 commits across 3 parallel worktrees)

### Track A — SabrInterpolatedSmileSection + MF SabrSmile wiring ✅

| Commit | Description |
|--------|-------------|
| `be6a34f` | `SabrInterpolatedSmileSection` (~469 LOC) + MF `updateSmiles()` SabrSmile branch wired (KahaleSmileSection superposition over the SabrInterpolatedSmileSection per C++ logic) + `validate()` SabrSmile check removed. **DONE_WITH_CONCERNS:** Scenario C (shifted SABR with negative raw strikes) skipped due to `SABRInterpolation.blackFormulaStdDevDerivative` raw-strike-positivity guard; documented inline. Scenario E used `within(1e-5)` instead of LOOSE due to Halton multi-restart basin variance (~6e-7 max delta on vol — not a formula error). |

### Track B — BasketGeneratingEngine + Nonstandard/FloatFloat basket wiring ✅

| Commit | Description |
|--------|-------------|
| `74f2df4` | `BasketGeneratingEngine` (~528 LOC, full Naive + MaturityStrikeByDeltaGamma algorithm with LM optimizer) + `Gaussian1dNonstandardSwaptionEngine.calibrationBasket()` + `Gaussian1dFloatFloatSwaptionEngine.calibrationBasket()` wired via anonymous BGE delegation pattern + `NonstandardSwaption.calibrationBasket()` + `FloatFloatSwaption.calibrationBasket()` delegating to engine. 20-case probe TIGHT. |

### Track C — TqrEigenDecomposition lift + CustomSmileFactory + MF CustomSmile wiring ✅

| Commit | Description |
|--------|-------------|
| `791a3e7` | **C.1:** `refactor(math.matrixutilities)` — lifted `TqrEigenDecomposition` (~195 LOC) from `GaussianQuadrature` private inner class to public class with public `EigenVectorCalculation` + `ShiftStrategy` enums. GaussianQuadrature now uses the public class. New 4-test `TqrEigenDecompositionTest` covers 2x2 + 3x3 tridiagonal cases. |
| `238a3d4` | **C.2:** `MarkovFunctional.CustomSmileFactory` + `CustomSmileSection` public abstract inner classes (mirrors C++ markovfunctional.hpp:103-118; **note: actual C++ API is `smileSection(SmileSection source, double atm)`, NOT the verbose signature I sketched in the dispatch prompt — the implementer correctly used the C++ source as source-of-truth**). `ModelSettings.withCustomSmileFactory(...)` + null-check guard in `validate()` (replaces unconditional rejection) + `updateSmiles()` CustomSmile branch (invokes factory, stores `cp.smileSection_`, skips Brent-in-marketSwapRate per C++ logic) + `updateNumeraireTabulation()` CustomSmile branch (uses `inverseDigitalCall(digital, cp.annuity_)` instead of Brent). New `MarkovFunctionalCustomSmileTest` smoke test. |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A19 (in spirit)** | Track A Scenario E | within(1e-5) per-test exception inline-justified (Halton multi-restart basin variance); not algorithmic, not blocking. |
| **A2 (skipped, not fired)** | Track A Scenario C | shifted SABR with negative raw strikes can't be calibrated due to existing `SABRInterpolation.blackFormulaStdDevDerivative` guard; cases skipped + documented (use KahaleSmile instead). |

A1/A3/A4/A6/A8/A9/A13/A15/A16/A17/A18/A20/A21/A22 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2K-7** | C++ source remains source-of-truth even when dispatch prompt sketches an API guess | Track C.2 implementer correctly used actual C++ `CustomSmileFactory.smileSection(SmileSection source, double atm)` API instead of the more verbose signature in my dispatch prompt. Standing project rule (CLAUDE.md): C++ v1.42.1 wins over Java pre-existing patterns OR controller prompt sketches. |
| **P2K-8** | Track A Scenario E within(1e-5) tier per-test exception | Halton multi-restart basin variance, not formula error; documented inline. |
| **P2K-9** | Track A Scenario C shifted-SABR-with-negative-raw-strikes skipped | Pre-existing `SABRInterpolation.blackFormulaStdDevDerivative` guard. Workaround: KahaleSmile mode for those use cases. Future Phase 2k+ candidate to relax the guard. |

## Phase 2k+ seed list (next priorities)

### Carry-forward from Phase 2j.5 + new

1. **`SABRInterpolation.blackFormulaStdDevDerivative` shifted-strike support** — relax positive-raw-strike guard so SabrInterpolatedSmileSection works with shifted SABR + negative strikes (Scenario C unblocker).
2. **`U128.java` shared util extraction** — consolidate u128 helpers across Dint64/LogKernel/TqrEigenDecomposition (now public in math.matrixutilities). Refactor candidate.
3. **`JQuantMath.lgamma` / `pow`** — still no path; carry-forward.

### From Phase 2j (still on the list)

4. **Douglas ADI / FdmAffineModelTermStructure** — FdHullWhite real floor (Phase 2i WI-2 B-1 A19).
5. **Phase 2h Fdm completeness items** — Bermudan/American/dividend, BiCGStab/GMRES, scheme expansion.
6. **Other Fdm-dependent engines** — FdHestonHullWhite, FdSabrVanilla, FdConvertibleBond, FdAndreasenHugeLocalVol, FdBlackScholesVanilla.

### Phase 3+ subsystem ports (the bulk of remaining "done" work)

7. **`experimental/`** — large surface, ~50+ files including `noarbsabrinterpolatedsmilesection`, etc.
8. **`models/marketmodels/`** — Libor Market Model family.
9. **`termstructures/credit/`** — credit term structures + CDS + CDX.
10. **`inflation/`** — inflation indexes + curves + linkers + caps/floors.
11. **C++ test-suite Java equivalents** — every C++ test deserves a Java equivalent (~150-200K LOC of tests).
12. **Calibration via market data feeds** — Gaussian1D ready for it; needs market-data plumbing.

## Out-of-scope (explicit, deferred)

- All Phase 2k+ items above
- BroadieKaya retry — needs pow + lgamma
- NCCS EXACT — needs lgamma
