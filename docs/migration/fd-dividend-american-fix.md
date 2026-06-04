# Finite-difference American discrete-dividend engine fix

**Date:** 2026-06-03
**Files:** `FDDividendAmericanEngine`, `FDDividendEngineAmerican` (new), `FDDividendEngineMerton73`; test `FDDividendAmericanEngineTest`
**Area:** `org.jquantlib.pricingengines.vanilla.finitedifferences`

## The bug

`FDDividendAmericanEngine` — the finite-difference engine for **American** options that pay **discrete cash dividends**, used by the `jquantlib-helpers` `FDAmericanDividendOptionHelper` — produced a value **completely invariant to the dividends**. An American call (or put) priced identically whether the dividend was 0 or 15:

```
American call, S=K=100, r=5%, vol=25%, 1y, dividend @ 6m:
   dividend 0  -> 12.3360
   dividend 15 -> 12.3360   (bit-for-bit identical — the dividend was ignored)
```

The European counterpart (`FDDividendEuropeanEngine`) was correct, so the dividend model itself was fine.

## Root cause — a phantom generic

C++ QuantLib (pre-1.17) expressed the engine as `FDEngineAdapter<FDAmericanCondition<FDDividendEngine>, …>`, where the wrapper inherits **from its template parameter**: `template<class baseEngine> class FDAmericanCondition : public baseEngine`. So `FDAmericanCondition<FDDividendEngine>` *is* an `FDDividendEngine` with an American early-exercise step condition layered on.

**Java cannot `extends T`.** The port's `FDAmericanCondition<T>` therefore always `extends FDStepConditionEngine` (the dividend-*free* base); the `<FDDividendEngine>` type argument was a **phantom that did nothing**. Consequently the dividend engine was never instantiated and `FDDividendEngineBase.setupArguments` — which reads the option's dividend schedule into the FD events — was never reached. The dividends simply vanished.

A second latent defect: `FDDividendEngineMerton73.executeIntermediateStep` re-installed the step condition via `super.initializeStepCondition()` (forcing the European `NullCondition`) instead of virtual dispatch — so even a dividend-aware American engine would have lost early exercise after the first dividend grid-rescale.

## The fix

1. **New `FDDividendEngineAmerican extends FDDividendEngineMerton73`** overriding `initializeStepCondition()` to install an `AmericanCondition` — the idiomatic-Java expression of `FDAmericanCondition<FDDividendEngine>`. It inherits the full discrete-dividend handling and adds early exercise at every FD step.
2. **`FDDividendAmericanEngine`** now uses `FDDividendEngineAmerican` as its `FDEngineAdapter` base (instead of the dividend-blind `FDAmericanCondition`).
3. **`FDDividendEngineMerton73.executeIntermediateStep`** re-installs the step condition via the virtual `initializeStepCondition()`, so the American condition survives each dividend rescale. This is a no-op for the European engine (which has no override → `NullCondition` either way).

## Cross-validation (TDD red → green)

`FDDividendAmericanEngineTest` was written first and confirmed red against the buggy engine, then green after the fix:

- **dividend dependence** — the American value now moves materially with the dividend (the regression assertion);
- **oracle** — the escrowed FD European value matches the C++-cross-validated `AnalyticDividendEuropeanEngine` (the Merton73 engine's javadoc states it is "consistent with the analytic version");
- **early-exercise premium** — American ≥ European for the same dividends;
- **invariance** — with no dividends the engine reduces to the plain `FDAmericanEngine`.

`DividendOptionTest` (18 tests) is unchanged, and the full `jquantlib` suite stays green.

## Ground-truth note

Per the project's ground-truth principle (C++ v1.42.1 is the source of truth): this FD dividend/American engine family (`FDDividendEngine`, `FDAmericanCondition`, `FDEngineAdapter`) was **deprecated and removed from C++ QuantLib in v1.17** and has **no counterpart in the pinned v1.42.1 source** — it survives only in JQuantLib and is consumed by `jquantlib-helpers`. The v1.42.1 path for FD American discrete-dividend pricing is `FdBlackScholesVanillaEngine` with `CashDividendModel.Spot` (already ported and cross-validated). A stale comment in `FDDividendAmericanEngine` that claimed a v1.42.1 typedef was corrected. A scan of the sibling `FDAmericanCondition<…>` / `FDShoutCondition<…>` uses confirmed they pass `FDStepConditionEngine` (matching the real base, so harmless) — this was the only genuinely-affected instance.
