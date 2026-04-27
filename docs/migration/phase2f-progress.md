# Phase 2f Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2f-plan.md` (commit `a788e51`)
**Design:** `docs/migration/phase2f-design.md` (commit `089eddb`)
**Predecessor:** `jquantlib-phase2e-complete` @ `a533fbd`
**Phase 2f start tip on main:** `a788e51`
**Baseline:** Tests `656/0/0/22`, scanner `0 stubs` (Phase 1 mandate met preserved)

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2f-A` | `phase-2f-A-cap-engines` | WI-1 cap engines: AnalyticCapFloor + BachelierCapFloor + BlackCapFloor Bachelier branch + OVS volType API |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2f-B` | `phase-2f-B-swaption-engines` | WI-2 swaption engines + G2.swaption: Jamshidian + BlackSwaption Bachelier branch + G2.swaption integral |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2f-C` | `phase-2f-C-heston-bk` | WI-3 Heston BroadieKaya + NCCS tightening: Lobatto + Laguerre + Fourier-inversion + 3 BroadieKaya schemes + NCCS tighten + discountBondOption + NCCV tier promotion |

All 3 worktrees were created off main tip `a788e51` at L0. All independent in the dep graph; launched in parallel.

## Pause-trigger status

- A4 sharpened (Lobatto/Laguerre/Fourier in scope, planned not surprise; commons-math3 OR minimal Complex port for Fourier): not fired
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8/A10/A11/A12 inactive per design §5
- A9 worktree-merge-conflict: not fired
- A13 NEW (NCCS structural drift impossibility): not fired
- A14 NEW (Complex arithmetic infrastructure gap): not fired

## Layer / WI progress

### L0 — pre-flight + worktree setup ✅

- L0.1 baseline confirmed (`656/0/0/22`, scanner 0 stubs, tip `a788e51`)
- L0.2 3 worktrees created off main tip `a788e51`

### L1 — parallel WI execution

#### WI-1 (worktree A) — Cap engines

- **5 commits landed on main** (last `a8a09a0`):
  - `33807a9` `align(termstructures.volatilities.optionlet): add volatilityType + displacement accessors`
  - `3d50dde` `stub(pricingengines.capfloor): port AnalyticCapFloorEngine + tight-tier fingerprint test`
  - **`762fad7`** `align(pricingengines): fix bachelierBlackFormula sign + parenthesization vs C++ v1.42.1` — **bonus pre-existing port bug**: pre-existing `BlackFormula.bachelierBlackFormula` used `optionType.ordinal()` (0/1) instead of `toInteger()` (±1) zeroing the Call branch and flipping the Put sign; missing parens left discount factor multiplying only the std-dev term. No production callers existed before WI-1, so no other tests had to move.
  - `2c79e28` `stub(pricingengines.capfloor): port BachelierCapFloorEngine + tight-tier fingerprint test`
  - `a8a09a0` `stub(pricingengines.capfloor,model.shortrate.calibrationhelpers): BlackCapFloorEngine Bachelier branch + CapHelper Normal-vol retrofit`
- **Tests:** +3 (Analytic cap + Bachelier cap + CapHelper Normal-vol case) all at TIGHT tier on first try.
- **Phase 2g seeds surfaced (not in WI scope):** `FloatingRateCoupon` constructor treats `withFixingDays(0)` as "use index default" (Java) vs C++ honors 0 as 0. Both probes + Java tests deliberately omit `.withFixingDays(0)` to keep fixtures self-consistent. Documented inline.
- **WI-1 fully complete.**

#### WI-2 (worktree B) — Swaption engines + G2.swaption

- **4 commits landed on main** (last `d04d02c`):
  - `289391e` `align(termstructures): SwaptionVolatilityStructure add volatilityType + shift accessors`
  - `a99e9c1` `stub(pricingengines.swaption): port JamshidianSwaptionEngine + loose-tier fingerprint test` (concern: see below)
  - `86690c1` `stub(pricingengines.swaption): BlackSwaptionEngine Bachelier branch + SwaptionHelper Normal-vol routing`
  - `d04d02c` `stub(model.shortrate.twofactormodels): G2.swaption integral path port + loose-tier fingerprint (closes Phase 2e A11 carve)`
- **Tests:** +3 (Jamshidian + Bachelier swaption + G2.swaption integral). Both Jamshidian and G2.swaption at LOOSE tier (not TIGHT) due to a discovered Brent solver divergence — see below.
- **Phase 2g seeds surfaced (deferred):**
  - **`Brent.solveImpl` pre-loop init diverges from C++**: Java seeds `root=xMax; froot=fxMax`; C++ first evaluates `f(guess)` to seed `root_/d/e`. Leaks ~5e-9 into converged root → 7e-11 NPV diff in JamshidianSwaptionEngine and similar in G2.swaption. Fix would re-fingerprint many Brent callers — out of scope. Documented inline in JamshidianSwaptionEngineTest + G2Test.
  - **VanillaSwap.setupArguments inverted-isAssignableFrom NOT surfaced** by Jamshidian / G2.swaption — both bypass it by reading directly from `args.swap.fixedLeg()`/`args.swap.nominal()`. Left for Phase 2g per plan.
  - **`Swaption.ArgumentsImpl` projection still incomplete** for callers needing fixed/floating-leg array projections; G2.swaption test manually populates fields. Engine-side, Jamshidian + G2 read coupon data from `args.swap` directly so no further accessors needed for those engines.
  - **`ConstantSwaptionVolatility` extended** with VolatilityType + shift constructor (additive overload + new fields, default-preserving). Required by SwaptionHelper Normal-vol routing.
- **WI-2 fully complete.** Phase 2e A11 carve closed (G2.swaption integral path).

#### WI-3 (worktree C) — Heston BroadieKaya + NCCS tightening
_(Pending — first implementer dispatched)_

### L2 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2f start (`a788e51`) | 656 | 0 | 0 | 22 | baseline |
| After WI-2 swaption engines (`d04d02c`) | 659 | 0 | 0 | 22 | +3 from B (Jamshidian + Bachelier swaption + G2.swaption); Brent divergence forces both Jamshidian and G2 to LOOSE tier |
| After WI-1 cap engines (`a8a09a0`) | 662 | 0 | 0 | 22 | +3 from A (Analytic cap + Bachelier cap + CapHelper Normal-vol) all TIGHT |
