# Phase 2e Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2e-plan.md` (commit `855f45d`)
**Design:** `docs/migration/phase2e-design.md` (commit `c4447ea`)
**Predecessor:** `jquantlib-phase2d-complete` @ `06450e6`
**Phase 2e start tip on main:** `855f45d`
**Baseline:** Tests `649/0/0/22`, scanner `work_in_progress: 1` (G2 only)

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2e-A` | `phase-2e-A-g2` | WI-1 G2 model body port (closes last scanner WIP) |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2e-B` | `phase-2e-B-cap-engine` | WI-2 BlackCapFloorEngine + CapFloor.NPV() wiring + CapHelper retrofit |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2e-C` | `phase-2e-C-swaption` | WI-3 Swaption infrastructure (BlackSwaption + DiscretizedSwaption + TreeSwaption) + SwaptionHelper full body |

All 3 worktrees were created off main tip `855f45d` at L0. All independent in the dep graph; launched in parallel after L0.

## Pause-trigger status

- A4 sharpened (new `pricingengines.swaption` directory in scope, planned not surprise; includes ConstantOptionletVolatility / ConstantSwaptionVolatility small mechanical helpers if needed): **FIRED on WI-3 first-chunk dispatch** — Java's `Swaption` instrument scaffold is missing entirely (no `Instrument`/`Option` parent, no Engine/Arguments/Results pattern, no `Settlement` enum). Inserted plan task C.0 (NEW): scaffold Swaption + Settlement infrastructure (~150 LOC port from C++ swaption.{hpp,cpp} + settlement.hpp). Then C.1+C.2+C.3 proceed as originally planned.
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8 inactive (G2 is two-factor, not one-factor family fan-out)
- A9 worktree-merge-conflict: not fired
- A10 inactive (no XABR work in 2e)
- A11 NEW (G2 swaption integral path needing non-trivial integrator): not fired
- A12 NEW (Swaption.NPV() wiring needing deeper engine-arguments dispatch refactor): not fired

## Layer / WI progress

### L0 — pre-flight + worktree setup ✅

- L0.1 baseline confirmed (`649/0/0/22`, scanner WIP=1, tip `855f45d`)
- L0.2 3 worktrees created off main tip `855f45d`

### L1 — parallel WI execution

#### WI-1 (worktree A) — G2 model body port

- **6 commits landed on main** (last `49aa24a`). Beyond the planned 3 (body + probe + tests), implementer correctly added 3 separate `align/fix/docs` commits per CLAUDE §4.2 for divergences discovered mid-stub:
  - **G2 body port** — Parameter indirection, Dynamics inner class, FittingParameter, all closed-form analytic helpers.
  - **`align(model.shortrate.twofactormodels): TwoFactorModel.tree(grid) isPositive=false`** (`6bc093a`) — TwoFactorModel was passing `isPositive=true` to TrinomialTree, causing ~5x divergence on tree.discount. Same kind of fix as Phase 2c WI-4 HW. Real port-correctness bug.
  - **`fix(harness): g2_probe skip terminal grid cell`** (`bb1a48f`) — terminal cell read C++ UB; matches BK/HW probe convention.
  - **G2Test** (3 methods): discount + discountBondOption at tight tier; tree fingerprint at loose tier (Brent solver in TermStructureFittingParameter, Phase 2c WI-5 BK precedent).
- **A11 fired (anticipated):** `G2.swaption(arguments, fixedRate, range, intervals)` left as `throw new UnsupportedOperationException("G2.swaption(...) deferred to Phase 2f")`. C++ path needs alignment of inner SwaptionPricingFunction's Brent-driven operator(), SegmentIntegral function-object operator() interface, and Swaption::arguments struct — 3 separate gaps. The model + tree paths (the primary value) are complete and tested.
- **WI-1 fully complete.** Scanner `work_in_progress: 1 → 0` — **the symbolic Phase 1 milestone is met**.

#### WI-2 (worktree B) — BlackCapFloorEngine + CapFloor.NPV() + CapHelper retrofit

- **`3edc015`** ✅ landed on main. `stub(pricingengines.capfloor): port BlackCapFloorEngine + CapFloor.NPV() wiring + CapHelper retrofit (Phase 2e WI-2)`. One atomic commit for B.1+B.2+B.3 as planned.
  - BlackCapFloorEngine full port (3 ctors + verbatim `calculate()` from C++ blackcapfloorengine.cpp:77-166).
  - CapFloor.java got Arguments/Results/Engine inner types + ArgumentsImpl/ResultsImpl DTOs + setupArguments verbatim from capfloor.cpp:210-269 + accessors + null-safe constructor for CapHelper compat.
  - CapHelper.java's blackPrice() body replaced (was Phase 2d "returns 0" stub) with C++ caphelper.cpp:69-89 implementation.
  - CapHelperTest extended with `modelValue_and_blackPrice_matchCpp` test at TIGHT tier (1e-12 rel + 1e-14 abs) — passes on first try, no tier-loosening needed.
  - caphelper_probe regenerated to add `model_value_and_black_price` case; new value `0.02268074725673492`.
- **A12 NOT fired** — Instrument/Engine plumbing was already in place via `Swap`'s pattern; CapFloor just needed the analogous types and the `setupArguments` body.
- **Concerns (non-blocking, Phase 2f follow-ups):**
  - `displacement_` hardcoded to 0.0 — Java's OptionletVolatilityStructure doesn't yet expose `volatilityType()`/`displacement()` (C++ added them in v1.42.1, Java was never retrofitted). All current call-sites pass shift=0, so behavior matches.
  - Java type-erasure forced dropping a convenience overload (`Handle<Quote>` and `Handle<OptionletVolatilityStructure>` erase to identical JVM signatures).
  - `additionalResults` (vega, optionletsPrice, optionletsVega) not populated — only `results_.value` written. Future-compatible no-op.
- **WI-2 fully complete.** Phase 2d WI-1's CapHelper vision retroactively completed: `modelValue` and `blackPrice` now produce real values matching C++ at tight tier.

#### WI-3 (worktree C) — Swaption infrastructure + SwaptionHelper full body

- **First implementer dispatch (C.1+C.2+C.3): BLOCKED — A4 trigger.** Java `Swaption` instrument is a near-empty stub: no `Instrument` parent, constructor body empty, `setupArguments` throws, no `Arguments`/`Results`/`EngineImpl`, no `Settlement` enum. `BlackSwaptionEngine` cannot compile without this scaffold. No commits made; worktree clean.
- **C.0 (`7c07ff6`)** ✅ landed on main. `align(instruments): scaffold Swaption + Settlement infrastructure`. Non-mechanical decisions: parent=Option (matches C++), payoff=null (Swaption.setupArguments deliberately doesn't call super.setupArguments to avoid Option's null-payoff trip), ArgumentsImpl extends Swap.ArgumentsImpl + holds VanillaSwap reference (composition-leaning hybrid because Java forbids C++'s multiple inheritance of FixedVsFloatingSwap::arguments + Option::arguments and VanillaSwap.ArgumentsImpl is non-static), Settlement enum with Type{Physical,Cash} + Method enum (PhysicalOTC, ParYieldCurve, CollateralizedCashPrice, etc.) + checkTypeAndMethodConsistency.
- **C.1+C.2+C.3 (in flight)** — second implementer dispatched to port BlackSwaptionEngine + probe + tight-tier test. Now that the scaffold is in place, BlackSwaptionEngine can extend `Swaption.EngineImpl` cleanly.

### L2 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2e start (`855f45d`) | 649 | 0 | 0 | 22 | baseline |
| After C.0 Swaption scaffold (`7c07ff6`) | 649 | 0 | 0 | 22 | scaffold-only, no behavior change |
| After WI-1 G2 (`49aa24a`) | 652 | 0 | 0 | 22 | +3 G2 tests; **scanner WIP=0** (symbolic milestone) |
| After WI-2 BlackCapFloorEngine (`3edc015`) | 653 | 0 | 0 | 22 | +1 CapHelper modelValue+blackPrice at tight tier |
