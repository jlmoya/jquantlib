# Phase 4 META Design — `ql/experimental/` Port Roadmap

**Status:** research / planning  
**Author:** research subagent, 2026-05-09  
**Scope:** all `ql/experimental/` subdirectories except `inflation/` (done in Phase 2s)  
**C++ pin:** v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`

---

## 1. Why Phase 4

Phases 1–3 completed the non-experimental QuantLib core: `ql/` (Phases 1–2), advanced FD engines
(2m), Gaussian1D (2j/2j.5), calibration (2r/2s/2t/2u), market models (3g/3h). The last large
greenfield area is `ql/experimental/`. These 24 subdirectories total **~63,700 LOC** of C++ (not
counting the already-done `inflation/`).

Phase 4 is a multi-sub-phase effort, each sub-phase targeting 3,000–6,000 LOC C++ and delivering
isolated Java packages with full cross-validated tests.

---

## 2. Complete Inventory

### 2.1 Raw counts (C++ v1.42.1, excluding `inflation/`)

| Subdir | .hpp | .cpp | LOC | Theme |
|--------|-----:|-----:|----:|-------|
| `credit` | 45 | 27 | 14,207 | CDO basket, latent models, CDS option, NTD |
| `volatility` | 23 | 15 | 14,247 | NoArbSABR (10K ABSprobs), SVI, ZABR, surface hierarchy |
| `math` | 23 | 11 | 5,714 | Copulas, PSO/firefly/HybridSA optimizers, latent model base |
| `commodities` | 23 | 19 | 4,466 | Energy, unit-of-measure, commodity curves |
| `barrieroption` | 10 | 6 | 3,880 | Double barrier, Vanna-Volga, MC/binomial/SuoWang |
| `finitedifferences` | 25 | 18 | 3,738 | OU/ExtOU/Kluge FD, VPP/storage/swing engines |
| `exoticoptions` | 22 | 7 | 2,071 | Himalaya/Everest/Pagoda, extensible, Asian Vecer |
| `callablebonds` | 7 | 6 | 1,974 | CallableBond, tree/black engines, const-vol |
| `coupons` | 8 | 7 | 1,728 | CMS-spread, digital CMS-spread, lognormal pricer |
| `mcbasket` | 8 | 4 | 1,703 | MC basket engine, Longstaff-Schwartz multi-path |
| `catbonds` | 5 | 4 | 965 | Catastrophe bonds, risk, MC engine |
| `asian` | 3 | 2 | 880 | Heston continuous/discrete geometric Asian analytic |
| `shortrate` | 3 | 2 | 811 | GeneralizedHullWhite, GeneralizedOU process |
| `variancegamma` | 7 | 6 | 1,011 | VG model, analytic + FFT engines |
| `varianceoption` | 3 | 2 | 624 | Variance option, Heston integral engine |
| `swaptions` | 4 | 3 | 1,078 | Hagan irregular swaption engine, IrregularSwap |
| `termstructures` | 3 | 2 | 1,052 | BasisSwapRateHelper, CrossCurrencyRateHelper |
| `processes` | 7 | 6 | 1,110 | ExtOU, Kluge-ExtOU, GemanRoncoroni, VegaStressed BS |
| `basismodels` | 4 | 3 | 749 | Swaption CFS, tenor optionlet/swaption VTS |
| `models` | 3 | 2 | 540 | NormalCLVModel, SquareRootCLVModel |
| `lattices` | 2 | 1 | 535 | ExtendedBinomialTree (already has Java stubs!) |
| `mcbasket` | — | — | — | (already counted above) |
| `forward` | 2 | 1 | 398 | AnalyticHestonForwardEuropeanEngine |
| `averageois` | 4 | 0 | 82 | ArithmeticAverageOIS (headers only) |
| `fx` | 3 | 0 | 60 | BlackDeltaCalculator, DeltaVolQuote (headers only) |
| `risk` | 3 | 0 | 57 | CreditRiskPlus, SensitivityAnalysis (headers only) |
| **TOTAL** | **253** | **154** | **~63,700** | |

**Notes on outliers:**
- `volatility/noarbsabrabsprobs.cpp` is 10,113 LOC — a single generated probability table; it
  translates to a Java constant array (likely gzip-loaded or computed on demand). LOC is misleading
  here: actual algorithmic content is ~4,100 LOC.
- `credit` is a near-complete CDO/basket ecosystem with heavy template metaprogramming (latent
  models). Java translation requires type-erasure strategy.
- `lattices/` already has full Java stubs (`ExtendedBinomialTree.java` + 9 concrete trees in
  `org.jquantlib.experimental.lattices`).
- `averageois/`, `fx/`, `risk/` are header-only thin wrappers; minimal porting effort.

---

## 2.2 Java current state

| Java package | Files today | Coverage |
|---|---|---|
| `org.jquantlib.experimental.inflation` | 13 | Phase 2s — DONE |
| `org.jquantlib.experimental.lattices` | 10 | Stubs present; body not cross-validated |
| everything else | 0 | NO Java counterpart |

The `experimental.lattices` stubs (`ExtendedBinomialTree`, 9 concrete trees) exist but were not
cross-validated against v1.42.1 in Phase 1. Phase 4d (lattices) must validate and complete them.

---

## 2.3 Test-suite coverage

| C++ test file | Experimental subdir(s) covered |
|---|---|
| `asianoptions.cpp` | `asian/` (Heston geometric tests) |
| `barrieroption.cpp`, `doublebarrieroption.cpp` | `barrieroption/` |
| `callablebonds.cpp` (1,050 LOC) | `callablebonds/` |
| `catbonds.cpp` | `catbonds/` |
| `commodityunitofmeasure.cpp` | `commodities/` (UoM portion) |
| `cmsspread.cpp`, `rangeaccrual.cpp` | `coupons/` |
| `cdo.cpp` (352), `cdsoption.cpp`, `nthtodefault.cpp` (382) | `credit/` |
| `everestoption.cpp`, `himalayaoption.cpp`, `pagodaoption.cpp`, `extensibleoptions.cpp` | `exoticoptions/` |
| `vpp.cpp` (943 LOC) | `finitedifferences/` |
| `normalclvmodel.cpp`, `squarerootclvmodel.cpp` | `models/` |
| `basketoption.cpp`, `mclongstaffschwartzengine.cpp` | `mcbasket/` |
| `variancegamma.cpp` (251) | `variancegamma/` |
| `varianceoption.cpp` | `varianceoption/` |
| `noarbsabr.cpp`, `svivolatility.cpp`, `zabr.cpp` | `volatility/` |
| `extendedtrees.cpp` | `lattices/` |
| `garch.cpp` (partial) | `volatility/` (GARCH lives in non-experimental) |

Subdirs without dedicated test files: `averageois/`, `basismodels/`, `forward/`, `fx/`,
`processes/`, `risk/`, `shortrate/`, `swaptions/`, `termstructures/`. These will use tests from the
broader test suite (e.g., `shortratemodels.cpp`, `swapforwardmappings.cpp`) or need probe-harness
coverage only.

---

## 3. Thematic Groupings

### Group A — Volatility Models (14,247 LOC)
`volatility/` — SABR variants (NoArbSABR, ZABR, SVI), surface hierarchies, extended
Black variance curves. Adds to the existing SABR-analytic (Phase 2r) and
AndreasenHuge (Phase 2m) work. High value: swaption vol cubes and equity FX surfaces.

### Group B — Credit Derivatives (14,207 LOC)
`credit/` — CDO basket, NTD, CDS option, latent-model family (Gaussian/Student copula,
saddle-point, binomial, recursive). Very self-contained but template-heavy. Java needs
generic bounds and type-erasure design. Pre-existing Java `termstructures/credit` is
non-experimental; experimental credit is orthogonal.

### Group C — Quantitative Math (5,714 LOC)
`math/` — Advanced optimizers (PSO, Firefly, Hybrid Simulated Annealing), copula RNGs,
convolved Student-t, Laplace interpolation, multi-dim quadrature. Adds to
`org.jquantlib.math.optimization`. Medium-value: some optimizers already present (BFGS, CG);
these are alternatives.

### Group D — Commodities / Energy (4,466 LOC)
`commodities/` — Full unit-of-measure system, energy swaps, energy futures, commodity curves,
pricing periods. Entirely new Java territory. Value depends on whether consumers use commodity
pricing; deferred-able.

### Group E — Barrier + Double Barrier Options (3,880 LOC)
`barrieroption/` — Double barrier with Vanna-Volga, SuoWang, MC, binomial, partial-time.
Extends existing `pricingengines/barrier/`. High value: FX barrier options are common.

### Group F — FD Energy Engines (3,738 LOC)
`finitedifferences/` — ExtOU/Kluge/VPP/Storage/Swing FD engines. Entirely new. Complex:
depends on `processes/` (ExtOU, Kluge) and non-experimental FD framework (Phase 2m).
Value: energy derivatives / VPP — niche but architecturally interesting.

### Group G — Exotic Options (2,071 LOC)
`exoticoptions/` — Himalaya, Everest, Pagoda (all MC), extensible options, Asian Vecer,
two-asset barrier/correlation, partial-time barrier, spread options. Mostly stand-alone
instruments + MC engines. Medium value.

### Group H — Callable Bonds (1,974 LOC)
`callablebonds/` — CallableBond instrument, tree/Black engines, const-vol structure.
High value: used widely for rate-model calibration. Depends on `ql/instruments/callablebond`
and tree methods.

### Group I — Coupons + Spreads (1,728 LOC)
`coupons/` — CMS-spread coupons, digital CMS-spread, lognormal pricer, proxy Ibor,
quanto coupon pricer. Extends existing cashflow/coupons. High value for IR desks.

### Group J — MC Basket (1,703 LOC)
`mcbasket/` — Multi-path MC basket engine, Longstaff-Schwartz multi-path pricer. Extends
existing `pricingengines/` MC framework. Medium value.

### Group K — Small Instruments / Structures (grouped, ~4,500 LOC total)
- `swaptions/` (1,078) — Hagan irregular swaption
- `termstructures/` (1,052) — BasisSwap + CrossCurrency rate helpers
- `processes/` (1,110) — ExtOU, Kluge-ExtOU, GemanRoncoroni, VegaStressedBS
- `variancegamma/` (1,011) — VG process/model + analytic + FFT engines
- `catbonds/` (965) — Catastrophe bonds
- `asian/` (880) — Heston Asian analytic
- `shortrate/` (811) — Generalized Hull-White

### Group L — Tiny / Header-only (< 250 LOC each)
- `varianceoption/` (624) — Variance option + Heston integral engine
- `basismodels/` (749) — Tenor VTS transformations
- `models/` (540) — CLV models
- `lattices/` (535) — Extended binomial trees (Java stubs exist)
- `forward/` (398) — Heston forward European engine
- `averageois/` (82) — Arithmetic average OIS (headers only)
- `fx/` (60) — Black delta calculator (header only)
- `risk/` (57) — CreditRiskPlus, sensitivity (header only)

---

## 4. Priority Assessment

### High Value (port early, frequently downstream-referenced)
1. **`callablebonds/`** — IR desks use callable bonds universally; well-tested (1,050 LOC test suite)
2. **`coupons/` (CMS-spread)** — CMS spreads are a standard IR product
3. **`volatility/`** (excl. 10K probs table) — SABR/SVI surfaces feed into swaption cubes
4. **`barrieroption/`** (double barrier) — FX/equity common; extends existing barrier infrastructure
5. **`termstructures/`** — BasisSwap + CrossCurrency helpers needed for multi-curve calibration
6. **`asian/`** — Heston Asian analytic fills gap in existing `pricingengines/asian/`
7. **`varianceoption/` + `variancegamma/`** — Variance products are self-contained + tested

### Medium Value (port when relevant subsystem is active)
8. **`models/` (CLV)** — Normalizing CLV model; useful when SLV/local-vol is active
9. **`shortrate/` (GeneralizedHW)** — Extends existing shortrate model family
10. **`forward/`** — Heston forward European; single file
11. **`mcbasket/`** — MC basket; depends on existing MC framework
12. **`exoticoptions/`** — Himalaya/Everest/Pagoda; niche multi-asset exotics
13. **`swaptions/`** (irregular) — Niche; Hagan irregular swaption

### Deferred / Niche (port in Phase 4 late or Phase 5)
14. **`math/`** (PSO/Firefly/HybridSA) — Alternative optimizers; low urgency
15. **`basismodels/`** (tenor VTS) — Specialized basis model transforms
16. **`credit/`** — Large; CDO basket latent models are highly specialized; heavy design work
17. **`finitedifferences/` (VPP/Kluge)** — Energy VPP; very niche; heavy FD prereqs
18. **`processes/` (ExtOU/Kluge)** — Only needed if FD energy engines ported
19. **`commodities/`** — Full UoM + energy swaps; valuable but standalone / deferred
20. **`catbonds/`** — Catastrophe bonds; extremely niche
21. **`averageois/`** — Headers only; trivial but wait for OIS context
22. **`fx/`** — BlackDeltaCalculator header; minimal
23. **`risk/`** — Header-only stubs; trivial
24. **`lattices/`** — Java stubs already present; complete + validate (easy win)

---

## 5. Recommended Phase Decomposition

### Phase 4a — "Quick wins": Lattices + Small Analytic Instruments
**LOC:** ~2,200 C++  
**Subdirs:** `lattices/` (535), `varianceoption/` (624), `forward/` (398), `asian/` (880) minus test  
**Why first:** lattices stubs already in Java — cross-validate and complete. The three analytic
engines (variance option, Heston forward European, Heston Asian) are self-contained, have C++
tests, and exercise the existing Heston + FD infrastructure.  
**Java packages:** `org.jquantlib.experimental.lattices` (complete), `pricingengines.vanilla` (add),
`pricingengines.asian` (add)  
**Test files:** `extendedtrees.cpp`, `varianceoption.cpp`, `asianoptions.cpp`

---

### Phase 4b — Callable Bonds
**LOC:** ~1,974 C++  
**Subdirs:** `callablebonds/` (1,974)  
**Why:** High value; dedicated test suite (1,050 LOC); well-bounded scope; depends only on
existing tree/lattice framework and `TermStructure`.  
**Java packages:** `org.jquantlib.experimental.callablebonds`  
**Test file:** `callablebonds.cpp`

---

### Phase 4c — Variance Gamma + Short-Rate Experimental
**LOC:** ~1,822 C++  
**Subdirs:** `variancegamma/` (1,011), `shortrate/` (811)  
**Why:** VG is isolated process + model + 2 engines; tested. GeneralizedHullWhite extends the
existing shortrate one-factor model hierarchy.  
**Java packages:** `org.jquantlib.experimental.variancegamma`, `model.shortrate` (extend)  
**Test files:** `variancegamma.cpp`, `shortratemodels.cpp` (partial)

---

### Phase 4d — Coupons: CMS-Spread + Quanto
**LOC:** ~1,728 C++  
**Subdirs:** `coupons/` (1,728)  
**Why:** CMS-spread products are high-value for IR; builds on existing `cashflows/coupons/` and
capfloor pricing engines from Phase 2j.  
**Java packages:** `org.jquantlib.experimental.coupons`  
**Test files:** `cmsspread.cpp`, `rangeaccrual.cpp`

---

### Phase 4e — Double Barrier Options
**LOC:** ~3,880 C++  
**Subdirs:** `barrieroption/` (3,880)  
**Why:** FX/equity double-barrier is common; extends Phase-2 barrier engine infrastructure.
Vanna-Volga and SuoWang are analytically demanding but self-contained.  
**Java packages:** `org.jquantlib.experimental.barrieroption`, `pricingengines.barrier` (extend)  
**Test files:** `barrieroption.cpp`, `doublebarrieroption.cpp`

---

### Phase 4f — Volatility Surfaces: SVI + ZABR + Extended SABR
**LOC:** ~4,134 C++ (excl. 10,113 LOC probability table)  
**Subdirs:** `volatility/` (14,247 total, but 10,113 LOC = generated table)  
**Why:** SVI and ZABR fill gaps in vol surface infrastructure. The NoArbSABR
`noarbsabrabsprobs.cpp` file is a data table — handle separately (lazy-load or Java resource).  
**Java packages:** `org.jquantlib.experimental.volatility`  
**Test files:** `noarbsabr.cpp`, `svivolatility.cpp`, `zabr.cpp`  
**Caution:** `noarbsabrabsprobs.cpp` (10,113 LOC) must become a binary resource or computed-on-demand;
not a literal Java class. Design required before implementation.

---

### Phase 4g — Term Structures + Basis Models
**LOC:** ~1,801 C++  
**Subdirs:** `termstructures/` (1,052), `basismodels/` (749)  
**Why:** BasisSwap + CrossCurrency rate helpers are multi-curve essentials. Tenor VTS
transformations support the swaption vol cube.  
**Java packages:** `org.jquantlib.experimental.termstructures`, `org.jquantlib.experimental.basismodels`  
**Test files:** `basisswapratehelpers.cpp`, `crosscurrencyratehelpers.cpp` (probe-harness only)

---

### Phase 4h — Exotic Options (Multi-asset MC)
**LOC:** ~2,071 C++  
**Subdirs:** `exoticoptions/` (2,071)  
**Why:** Himalaya, Everest, Pagoda are MC-based; self-contained instruments + engines.
Extensible options and partial-time barrier complete the set.  
**Java packages:** `org.jquantlib.experimental.exoticoptions`, `pricingengines.exotic` (new)  
**Test files:** `himalayaoption.cpp`, `everestoption.cpp`, `pagodaoption.cpp`, `extensibleoptions.cpp`

---

### Phase 4i — MC Basket + Swaptions
**LOC:** ~2,781 C++  
**Subdirs:** `mcbasket/` (1,703), `swaptions/` (1,078)  
**Why:** MC basket pricer and Hagan irregular swaption are both moderate complexity; group
them as a Monte Carlo wave.  
**Java packages:** `org.jquantlib.experimental.mcbasket`, `org.jquantlib.experimental.swaptions`  
**Test files:** `basketoption.cpp`, `mclongstaffschwartzengine.cpp`

---

### Phase 4j — CLV Models + Forward Engine
**LOC:** ~938 C++  
**Subdirs:** `models/` (540), `forward/` (398)  
**Why:** Both are short, self-contained, tested. NormalCLVModel and SquareRootCLVModel extend
the local-vol / Gaussian1D calibration work (Phase 2j). Forward Heston engine is a single file.  
**Java packages:** `org.jquantlib.experimental.models`  
**Test files:** `normalclvmodel.cpp`, `squarerootclvmodel.cpp`, `forwardoption.cpp`

---

### Phase 4k — Advanced Math: Copulas + Alternative Optimizers
**LOC:** ~5,714 C++  
**Subdirs:** `math/` (5,714)  
**Why:** Copula RNGs support the credit latent models (Phase 4m). Particle swarm, firefly,
and hybrid SA are alternative global optimizers. Multi-dim quadrature supplements existing
integration.  
**Java packages:** `org.jquantlib.experimental.math`, `math.optimization` (extend)  
**Test files:** probe-harness only (no dedicated test-suite file)

---

### Phase 4l — Catastrophe Bonds + AverageOIS + FX + Risk stubs
**LOC:** ~1,164 C++  
**Subdirs:** `catbonds/` (965), `averageois/` (82), `fx/` (60), `risk/` (57)  
**Why:** Clean up remaining small subdirs; cat bonds have a test; averageois/fx/risk are
header-only trivial ports.  
**Java packages:** `org.jquantlib.experimental.catbonds`, small additions to existing  
**Test files:** `catbonds.cpp`

---

### Phase 4m — Credit Experimental (CDO + Latent Models)
**LOC:** ~14,207 C++  
**Subdirs:** `credit/` (14,207)  
**Why last:** Most complex; depends on Phase 4k (copula RNGs), Phase 3g (CDS instruments),
and requires Java generics design for C++ template latent models. Saddle-point model
(1,365 LOC) and recursive loss model (494 LOC) are template-heavy.  
**Java packages:** `org.jquantlib.experimental.credit`  
**Test files:** `cdo.cpp`, `cdsoption.cpp`, `nthtodefault.cpp`

---

### Phase 4n — FD Energy Engines (VPP / Swing / Storage)
**LOC:** ~4,848 C++ (finitedifferences 3,738 + processes 1,110)  
**Subdirs:** `finitedifferences/` (3,738), `processes/` (1,110)  
**Why last:** Depends on non-experimental FD framework (Phase 2m), ExtOU process, and Kluge
process (all new Java). Energy VPP/storage/swing is the most niche subsystem.  
**Java packages:** `org.jquantlib.experimental.finitedifferences`, `org.jquantlib.experimental.processes`  
**Test files:** `vpp.cpp` (943 LOC)

---

### Phase 4o — Commodities
**LOC:** ~4,466 C++  
**Subdirs:** `commodities/` (4,466)  
**Why deferred:** Commodity pricing is entirely separate from rates/equity; lowest dependency
from any other Phase 4 work; defer unless consumer demand identified.  
**Java packages:** `org.jquantlib.experimental.commodities`  
**Test files:** `commodityunitofmeasure.cpp`

---

## 6. Phase Ordering Rationale

```
Phase 4a  (lattices + small analytic)          — quick win, Java stubs exist
Phase 4b  (callable bonds)                     — high value, bounded scope
Phase 4c  (variance gamma + shortrate exp)     — small, isolated
Phase 4d  (CMS-spread coupons)                 — high value, IR essential
Phase 4e  (double barrier options)             — high value FX
Phase 4f  (vol surfaces SVI/ZABR/NoArbSABR)   — vol infrastructure
Phase 4g  (termstructures + basismodels)       — multi-curve support
Phase 4h  (exotic options multi-asset)         — MC exotics
Phase 4i  (MC basket + swaptions)              — MC wave continuation
Phase 4j  (CLV models + forward Heston)        — model extensions
Phase 4k  (advanced math copulas + optimizers) — prereq for credit
Phase 4l  (catbonds + tiny headers)            — cleanup
Phase 4m  (credit experimental)                — complex, deferred
Phase 4n  (FD energy engines + processes)      — VPP/swing, niche
Phase 4o  (commodities)                        — standalone, lowest priority
```

Total estimated C++ LOC across all phases: ~63,700.  
At historical throughput of ~3,500 LOC C++/phase, this is approximately 15–18 sub-phases.

---

## 7. Java Package Mapping

All new experimental Java code goes under `org.jquantlib.experimental.*` mirroring the C++
directory structure:

| C++ subdir | Java package |
|---|---|
| `experimental/lattices/` | `org.jquantlib.experimental.lattices` (stubs already) |
| `experimental/volatility/` | `org.jquantlib.experimental.volatility` |
| `experimental/credit/` | `org.jquantlib.experimental.credit` |
| `experimental/math/` | `org.jquantlib.experimental.math` |
| `experimental/commodities/` | `org.jquantlib.experimental.commodities` |
| `experimental/barrieroption/` | `org.jquantlib.experimental.barrieroption` |
| `experimental/finitedifferences/` | `org.jquantlib.experimental.finitedifferences` |
| `experimental/exoticoptions/` | `org.jquantlib.experimental.exoticoptions` |
| `experimental/callablebonds/` | `org.jquantlib.experimental.callablebonds` |
| `experimental/coupons/` | `org.jquantlib.experimental.coupons` |
| `experimental/mcbasket/` | `org.jquantlib.experimental.mcbasket` |
| `experimental/catbonds/` | `org.jquantlib.experimental.catbonds` |
| `experimental/asian/` | engines go in `org.jquantlib.pricingengines.asian` |
| `experimental/shortrate/` | extends `org.jquantlib.model.shortrate` |
| `experimental/variancegamma/` | `org.jquantlib.experimental.variancegamma` |
| `experimental/varianceoption/` | `org.jquantlib.experimental.varianceoption` |
| `experimental/swaptions/` | `org.jquantlib.experimental.swaptions` |
| `experimental/termstructures/` | extends `org.jquantlib.termstructures` |
| `experimental/basismodels/` | `org.jquantlib.experimental.basismodels` |
| `experimental/models/` | `org.jquantlib.experimental.models` |
| `experimental/forward/` | extends `org.jquantlib.pricingengines.vanilla` |
| `experimental/processes/` | extends `org.jquantlib.processes` |
| `experimental/averageois/` | extends `org.jquantlib.instruments` |
| `experimental/fx/` | extends `org.jquantlib.pricingengines.vanilla` |
| `experimental/risk/` | `org.jquantlib.experimental.risk` |

---

## 8. Known Design Concerns

### D1. `noarbsabrabsprobs.cpp` (10,113 LOC — generated table)
This file contains a large numerical probability table used by NoArbSABR interpolation. Options:
- (A) Pre-compute in Java and serialize to a binary resource file (loaded at class-init). **Preferred.**
- (B) Compute on-demand using the underlying algorithm. Slower but eliminates data file.
- (C) Translate literally as a `double[][]` constant. Risk: JVM class file size limit (65535 bytes
  per method); requires splitting. Not recommended.
Decision needed in Phase 4f design doc.

### D2. `credit/` latent models — C++ template → Java generics
C++ uses `LatentModel<COPIULA>`, `BasketLossModel<LM>` etc. Java needs bounded wildcards or
abstract base classes with type-erasure. The `latentmodel.hpp` (802 LOC) is the template base.
Design decision needed in Phase 4m design doc.

### D3. `experimental/lattices/` stubs not cross-validated
The 10 existing Java `ExtendedBinomialTree` classes were written in Phase 1 without C++
cross-validation. Phase 4a must verify each against v1.42.1 reference output before declaring done.

### D4. `processes/` depends on `finitedifferences/` (FD experimental)
`extouwithjumpsprocess`, `klugeextouprocess` are only useful with `fdextoujumpvanillaengine` etc.
Port together in Phase 4n.

### D5. `averageois/` — header-only, no .cpp
ArithmeticAverageOIS is implemented as inline/template header. Java port is straightforward but
runtime behavior must be verified against v1.42.1 (no .cpp means no standalone compile unit to test).

---

## 9. Quality Gates (same as all prior phases)

- Every sub-phase: `mvn -pl jquantlib test` green before commit.
- TDD: cross-validate each class against C++ v1.42.1 via `migration-harness/` probes.
- Tolerance: exact / tight 1e-12 / loose 1e-8 per design §6.
- One commit per logical unit (instrument, engine, or related cluster).
- No `Co-authored-by: Claude` trailer; `-s` Signed-off-by.

---

## 10. Open Questions for Controller

1. **Phase 3i (evolvers) and 3j parallel research** — does Phase 4a start immediately after they
   land, or after Phase 3 is fully done?
2. **`noarbsabrabsprobs.cpp` strategy** — binary resource file (D1 option A) requires harness
   tooling to generate it from C++. Approve approach before Phase 4f starts.
3. **Credit latent model strategy** — Java generics design (D2) is non-trivial. Should this be a
   separate architecture review sub-phase before Phase 4m implementation?
4. **Commodities (Phase 4o) — skip or defer?** No known downstream consumer in JQuantLib Java
   test suite. Recommend defer to Phase 5 unless there is a concrete consumer.
