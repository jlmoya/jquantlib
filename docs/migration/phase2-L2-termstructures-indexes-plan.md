# L2 termstructures + indexes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> to implement this plan task-by-task.

**Goal:** Port 73 termstructures + indexes classes from C++ v1.42.1 to Java with
TDD cross-validation, on JDK 25 LTS + W1-W4 modernized + L1 closure base.

**Architecture:** L2 builds on L1 math primitives. Indexes are mostly thin
ibor-index subclasses with tenor and currency parameters; termstructures are
volatility surfaces, fitted curves, helpers. Many ibor entries are
tenor-specific subclasses of a base (Bbsw → Bbsw1M, Bbsw2M, ...).

**Tech Stack:** JDK 25, Maven, JUnit 4.13.2.

---

## Scope

### indexes (41)

**indexes root (3):**
- CaseInsensitiveCompare — comparator helper
- CustomRegion — inflation custom region (probably for InflationIndex)
- OvernightIndexedSwapIndex — OIS index variant

**indexes/ibor (38):**
- **Aonia** — Australian overnight (~50 LOC)
- **Bbsw** + 6 tenor variants (Bbsw1M..Bbsw6M) — Bank Bill Swap Rate
- **Numerous standard ibor indexes** — verify which are genuinely missing vs already present (Java has Euribor, Libor, etc.)

Run grep to enumerate exact missing list (audit script identified 38):
```bash
python3 -c "..." # already in audit
```

### termstructures (32)

**termstructures root (2):**
- InterpolatedCurve — base curve template (likely covered by existing Java InterpolatedCurves)
- RelativeDateBootstrapHelper — bootstrap helper base

**termstructures/inflation (1):**
- ZeroInflationTraits — bootstrap traits for zero inflation

**termstructures/volatility (6):**
- AtmAdjustedSmileSection — ATM-adjusted smile section
- ZabrFullFd, ZabrInterpolatedSmileSection, ZabrLocalVolatility, ZabrShortMaturityLognormal, ZabrShortMaturityNormal — ZABR family

**termstructures/volatility/capfloor (1):**
- ConstantCapFloorTermVolatility

**termstructures/volatility/equityfx (3):**
- AndreasenHugeCostFunction, GridModelLocalVolSurface, SingleStepCalibrationResult

**termstructures/volatility/inflation (2):**
- CPIVolatilitySurface, ConstantCPIVolatility

**termstructures/volatility/swaption (9):**
- CmsMarket, CmsMarketCalibration, PrivateObserver, SwaptionVolCubeSabrModel, SwaptionVolCubeZabrModel, XabrModelTraits

**termstructures/yield (8):**
- CPIBondHelper, CubicBSplinesFitting, ExponentialSplinesFitting, FxSwapRateHelper, NaturalCubicFitting, SpreadFittingMethod, SpreadTraits

---

## Clusters for parallel dispatch

- **L2-A**: indexes/ibor BBSW + Aonia + indexes root (small, ~15 small classes)
- **L2-B**: termstructures/yield (CPIBondHelper, FxSwapRateHelper, fitting methods, SpreadTraits — 8 classes, ~600-800 LOC)
- **L2-C**: termstructures/volatility ZABR family + AtmAdjustedSmileSection (~6 classes)
- **L2-D**: termstructures/volatility {capfloor, equityfx, inflation, swaption} (15 classes)
- **L2-E**: indexes/ibor remaining (audit which other 30+ ibors are truly missing; many may be present)

---

## Per-class TDD template

(Same 5-step cycle as L1-B/C/D/E plans)

1. Read C++ source
2. Write failing test (probe-derived expected values)
3. Verify fail
4. Implement
5. Verify pass
6. Commit per logical batch with `-s` sign-off

JDK 25 idioms naturally (records for traits/result DTOs, var, switch expressions, pattern matching, sealed types where applicable).

---

## Sequencing

1. L2-A (indexes pilot) first
2. L2-B, L2-C, L2-D, L2-E in parallel after L2-A lands

---

## Definition of done

- All L2 entries either ported OR documented SKIP (already-present-as-different-class)
- Full suite still 3270+/0/0 baseline (no regression)
- L1 + L2 combined tag: `jquantlib-phase2-l2-complete`
