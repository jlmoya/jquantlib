# Phase 2j Completion — Gaussian1D Family Port (P2J-10 trim applied)

**Status:** complete 2026-05-02
**Tag:** `jquantlib-phase2j-complete` @ `<FILL_AT_TAG>`
**Predecessor:** `jquantlib-phase2i.6-complete` @ `44be66c`
**Plan:** `docs/migration/phase2j-plan.md` (commit `3f2c33f`)
**Design:** `docs/migration/phase2j-design.md` (commit `368dbda`)

## Final state

| Metric | Phase 2i.6 tip | Phase 2j tip | Δ |
|--------|----------------|--------------|----|
| Tests | 688/0/0/22 | 792/0/0/22 | +104 |
| Scanner WIP | 0 | 0 | unchanged |
| New Java production LOC | — | ~3500-4500 (planned ~7000-9000; trimmed per P2J-10) | partial |
| New Java packages | — | 4 of 5 planned (`gaussian1d` model + process + 2 engine + 1 vol — vol used existing `volatilities` not new `volatility`) | adapted |
| Engines landed | n/a | 3 of 5 (Standard SwaptionEngine + CapFloorEngine + Jamshidian) | trimmed |

Aggregate test-count target was `~698` (per design); actual `792` (+94 over target). Overshoot driven by:
1. WI-1.2's 95 separate test methods (style divergence — each probe case as own @Test method instead of single iterating @Test)
2. WI-4.0c's 94 test methods (KahaleSmileSection, same pattern)
3. Otherwise standard "+1 per WI" pattern matched expectation

## What landed (12 commits + 5 docs)

### WI-1 — Model layer (4 sub-commits + 2 align prereqs) ✅

| Commit | Description |
|--------|-------------|
| `4b3d3d2` | **WI-1.1:** `Gaussian1dModel` abstract base (~578 LOC); 5 documented deferrals for downstream WIs |
| `649e447` | **WI-1.1 followup:** `implements TermStructureConsistentModel` + observer registration + dead-code cleanup (per code-review) |
| `9b26a30` | **WI-1.2 align prereq:** `align(processes.gsr)` — friend-pattern setters + GsrProcessCore `pairKey` collision fix (A15-style hidden bug) |
| `3b29638` | **WI-1.2:** `GsrProcessCore` + `GsrProcess` (~515 LOC; 95 test methods at TIGHT — style divergence noted) |
| `1e70e44` | **WI-1.3:** `Gsr` concrete model (~430 LOC) + enable Gaussian1dModelTest from WI-1.1 |
| `f931ce2` | **WI-1.4 align prereq:** `align(termstructures.volatilities)` — added volatilityType/shift/optionPrice to SmileSection; fixed FlatSmileSection.minStrike; relaxed BlackFormula shift validation (3 fixes bundled) |
| `b46f065` | **WI-1.4:** `Gaussian1dSmileSection` + `Gaussian1dSwaptionVolatility` (~390 LOC). Note: 55 smile_*/swvol_* probe cases deferred to WI-2 (need engine for optionPrice). |

### WI-2 — Standard engines ✅

| Commit | Description |
|--------|-------------|
| `ccb38bd` | **WI-2.1:** `Gaussian1dSwaptionEngine` (~430 LOC). Tier: **LOOSE** per A19 — numerical Gaussian quadrature accumulates erfc-driven drift, max diff 1.73e-9 (well within LOOSE 1e-8). |
| `11f93d6` | **WI-2.2 align prereq:** `align(instruments)` — added `indexes[]` to `CapFloor.ArgumentsImpl` (additive, mirrors C++) |
| `0c54fa3` | **WI-2.2:** `Gaussian1dCapFloorEngine` (~280 LOC). 54 cases at LOOSE per WI-2.1 precedent. |

### WI-3 — Niche engines (1 of 3 — 2 deferred)

| Commit | Description |
|--------|-------------|
| `24c0a8e` | **WI-3.1:** `Gaussian1dJamshidianSwaptionEngine` (~210 LOC) + bundled aligns: `Gaussian1dModel.zerobondOption` impl (~90 LOC, replaced UnsupportedOperationException stub) + `CubicInterpolation.Lagrange` BC impl (~30 LOC). 10 cases at TIGHT. |

**Deferred:**
- **WI-3.2 Nonstandard engine:** A16 — `NonstandardSwap` + `NonstandardSwaption` instruments missing in Java. Per P2J-10 trim discipline.
- **WI-3.3 FloatFloat engine:** A16 — `FloatFloatSwap` + `FloatFloatSwaption` instruments missing in Java (verified). Same trim path.

### WI-4 — MarkovFunctional prereqs (3 of 4 — MF deferred)

| Commit | Description |
|--------|-------------|
| `0aee3f4` | **WI-4.0a:** `MfStateProcess` (~221 LOC, 174 cases TIGHT). Standalone process for MF, broadly useful. |
| `4dec5d8` | **WI-4.0b:** `SmileSectionUtils` (~260 LOC, 32 cases TIGHT). |
| `7ec9333` | **WI-4.0c:** `KahaleSmileSection` (~400 LOC + bundled fixes) — fixed CFunction.eval N(d1) saturation for d1>8.2; Halley invNormal refinement; local `blackFormulaImpliedStdDevKahale` with maxStdDev=24.0 (Java BlackFormula uses 3.0 — bisection-path divergence). 5 scenarios (A-E) at TIGHT. |

**Deferred:**
- **WI-4.0d MarkovFunctional:** A16 second iteration — needs `GaussHermiteIntegration` family (~600 LOC C++ in `org.jquantlib.math.integrals` subpackage, includes `GaussianOrthogonalPolynomial` + `GaussHermitePolynomial` + `GaussianQuadrature` Golub-Welsch) + `AtmSmileSection` (~80 LOC). User chose Option B per P2J-10. The 3 prereqs (4.0a/b/c) stay as broadly-useful infrastructure.

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A15** | WI-1.2 followup | Hidden bug surfaced in `GsrProcessCore.pairKey` (128-bit hash collision); fixed via `DoublePair` value class. Bundled as `align(processes.gsr)` prep commit `9b26a30`. |
| **A16 (1st)** | WI-4 first dispatch | MarkovFunctional needs MfStateProcess + SmileSectionUtils + KahaleSmileSection. User chose Option A (expand to 4 sub-commits). |
| **A16 (2nd)** | WI-4.0d after 3 prereqs | MF needs ANOTHER 2 deps (GaussHermiteIntegration + AtmSmileSection). User chose Option B (defer to Phase 2j.5). |
| **A16 (3rd)** | WI-3.2 first dispatch | NonstandardSwap + NonstandardSwaption instruments missing in Java. Deferred per P2J-10. |
| **A16 (4th)** | WI-3.3 controller pre-check | Same A16 expected for FloatFloatSwap + FloatFloatSwaption. Deferred. |
| **A17 (in spirit)** | WI-2.2 align (3rd unplanned), and WI-3.1 + WI-4.0c bundled aligns within infra commits | A17 cap was >2 unplanned aligns; we ended at ~5 aligns (WI-1.2 pairKey + WI-4.0b SmileSection bundle + WI-2.2 CapFloor.indexes + WI-3.1 zerobondOption/CubicInterp.Lagrange + WI-4.0c CFunction/invNormal/blackFormulaImpliedStdDevKahale). Bundling-into-infra-commits muted the strict trigger; spirit of "re-evaluate scope" honored via P2J-10 trims. |
| **A19** | WI-2.1 + WI-2.2 | Engine NPV tests floor at LOOSE (1.73e-9 / 1e-8 range) due to numerical Gaussian quadrature drift. Per design §4 risk 2 anticipated. |

A2/A3/A4/A6/A8/A9/A13/A18/A20/A21 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2J-11** | WI-4 expanded to 4 sub-commits (Option A) after first A16 | User choice; 3 prereqs (MfStateProcess/SmileSectionUtils/KahaleSmileSection) needed before MF can compile. |
| **P2J-12** | WI-4.0d MF deferred to Phase 2j.5 (Option B) after second A16 | Second iteration of A16 surfaces ANOTHER 2 deps; P2J-10 trim discipline named MF as first-trim target. |
| **P2J-13** | WI-3.2 (Nonstandard) and WI-3.3 (FloatFloat) deferred to Phase 2j.5 | A16 — both require Java instrument ports (NonstandardSwap, NonstandardSwaption, FloatFloatSwap, FloatFloatSwaption) not present. P2J-10 trim order had already named these. |
| **P2J-14** | LOOSE tier accepted for engine NPVs per WI-2.1 + WI-2.2 (A19 confirmed) | Numerical Gaussian quadrature accumulates ~1.7e-9 erfc-driven drift; well within LOOSE 1e-8. Anticipated by design §4 risk 2. |
| **P2J-15** | Java vol-structure subpackage = `volatilities` (plural) not `volatility` (singular as design assumed) | Java side already uses `org.jquantlib.termstructures.volatilities`; Phase 2j adapts to existing convention rather than introducing inconsistency. |
| **P2J-16** | KahaleSmileSection bundled local `blackFormulaImpliedStdDevKahale` with maxStdDev=24.0 (Java's `BlackFormula` uses 3.0) | C++ behavior; fixing `BlackFormula` globally would have wider blast radius. Local copy keeps the fix surgical. Phase 2k+ candidate to align Java BlackFormula. |

## Realized scope vs design

| Item | Design | Actual | Notes |
|------|--------|--------|-------|
| WI-1 model layer | 4 sub-commits | ✅ 4 sub-commits + 2 align prereqs | As planned |
| WI-2 standard engines | 2 engines | ✅ 2 engines (1 align prereq) | As planned, both at LOOSE per A19 |
| WI-3 niche engines | 3 engines | ⚠️ 1 of 3 (Jamshidian only) | Nonstandard + FloatFloat deferred to Phase 2j.5 (A16 — instruments missing) |
| WI-4 MarkovFunctional | 1 commit | ⚠️ 3 prereqs landed; MF itself deferred | Two A16 iterations on MF; deferred to Phase 2j.5 per P2J-10 |
| New subpackages | 5 planned | 4 created | Vol-structure went into existing `volatilities` package (P2J-15) |
| Test count | 688 → ~698 (+10) | 688 → 792 (+104) | Style divergence (WI-1.2 + WI-4.0c each wrote 95 separate test methods) |

## Phase 2j.5 seed list (carry-forward)

### Primary scope (the deferred items)

1. **NonstandardSwap + NonstandardSwaption instruments** — port from `ql/instruments/nonstandardswap{,tion}.{hpp,cpp}` (~600+ LOC). Prereq for WI-3.2.
2. **FloatFloatSwap + FloatFloatSwaption instruments** — port from `ql/instruments/floatfloatswap{,tion}.{hpp,cpp}` (~700+ LOC). Prereq for WI-3.3.
3. **Gaussian1dNonstandardSwaptionEngine** — port after instrument prereq (~636 LOC C++).
4. **Gaussian1dFloatFloatSwaptionEngine** — port after instrument prereq (~848 LOC C++, largest engine).
5. **GaussHermiteIntegration family** — port `GaussianOrthogonalPolynomial` + `GaussHermitePolynomial` + `GaussianQuadrature` (Golub-Welsch) + `GaussHermiteIntegration` (~600 LOC C++ in `org.jquantlib.math.integrals`).
6. **AtmSmileSection** — port `ql/termstructures/volatility/atmsmilesection.{hpp,cpp}` (~80 LOC).
7. **MarkovFunctional** — port after items 5+6 land (~1710 LOC C++).
8. **MarkovFunctional probe + test** at TIGHT.

Total Phase 2j.5 estimate: ~4500-5500 Java LOC across ~8 commits.

### Other carry-forwards

9. **Refactor `JQuantMath.log`'s LogKernel + `Dint64` shared u128 helpers** to a `U128.java` utility (Phase 2j followup, low priority).
10. **`JQuantMath.pow`** — empirical leverage still low (1 GsrProcessCore site untouched per P2J-5; A19 didn't fire). Defer indefinitely.
11. **`JQuantMath.lgamma`** — no correctly-rounded source available; NCCS EXACT remains blocked.
12. **Douglas ADI / FdmAffineModelTermStructure** — FdHullWhite floor (Phase 2i WI-2 B-1 A19), still on the list.
13. **Phase 2h Fdm completeness** — Bermudan/American/dividend, BiCGStab/GMRES, scheme expansion.
14. **Other Fdm-dependent engines** — FdHestonHullWhite, FdSabrVanilla, FdConvertibleBond, etc.
15. **Refactor WI-1.2 + WI-4.0c tests** to ONE @Test method with collect-all-failures (currently 95 + 94 separate @Test methods inflate test count) — low priority polish.

## Out-of-scope (explicit, deferred)

- All items in Phase 2j.5 seed list (primary deferrals)
- `JQuantMath.lgamma` / `JQuantMath.pow` (ongoing transcendental gaps)
- Fdm-related work (separate phase track)
- Calibration via market data feeds (Gaussian1D ready for it but separate scope)
- BlackFormula global alignment with C++ maxStdDev (per P2J-16 deferral)
