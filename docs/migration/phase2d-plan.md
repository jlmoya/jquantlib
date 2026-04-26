# Phase 2d Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Each WI runs in its own git worktree (see L0 setup); all 3 worktrees A/B/C run concurrently after L0.

**Goal:** Land the three Phase 2d work items per `docs/migration/phase2d-design.md`: WI-1 CapHelper unstub via `BlackCalibrationHelper` port, WI-2 Heston `NonCentralChiSquareVariance` discretization scheme using Phase 2c WI-1's chi-squared CDF, WI-3 SABR Halton multi-restart via faithful `XABRInterpolation` + `XABRInterpolationImpl<Model>` + `XABRCoeffHolder<Model>` scaffold (un-skips the 2 currently-`@Ignore`'d SABR calibration tests). End state: scanner reports `work_in_progress: 1` (only G2 — Phase 2e seed), 0 `not_implemented`, 0 `numerical_suspect`; tag `jquantlib-phase2d-complete`.

**Architecture:** Same as Phase 2c — direct commits to `main`, TDD per stub, cross-validated against C++ QuantLib v1.42.1 via `migration-harness/` probes, tolerance tiers (exact/tight/loose). 3 git worktrees per `phase2d-design.md` §3 — A=WI-1 CapHelper, B=WI-2 Heston NCCV, C=WI-3 SABR/XABR/Halton. Each worktree fast-forwards to `main` async as its full-suite passes; controller orchestrates rebases between landings (Phase 2c lesson: always merge from main checkout, never from inside a worktree's cwd). Pause triggers per design §5: A6 disabled, A4 sharpened (BroadieKaya carve gate inside WI-2 if Lobatto/Laguerre needed — but NCCV alone shouldn't need them), A8 inactive, A9 worktree-merge-conflict, A10 NEW for XABR template-to-generics translation snags.

**Tech Stack:** Java 11 / Maven / JUnit 4 (existing); C++17 / CMake / QuantLib v1.42.1 pinned via submodule (commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`); Python 3 for scanner tooling; nlohmann/json for probe output; git worktrees for parallel implementer execution.

---

## Overview

| Layer | Description | Worktree | Expected commits |
|-------|-------------|----------|------------------|
| L0 | Pre-flight: confirm baseline, snapshot scanner, set up 3 worktrees | (main) | 0 |
| L1 | All 3 worktrees launch in parallel | A, B, C | 4–6 each |
| L2 | Completion doc + tag | (main) | 1 commit + tag |

**Non-goals reminder (design §1):** BroadieKaya×3 Heston schemes deferred to Phase 2e (need Lobatto/Laguerre integrator ports); SwaptionHelper unstub deferred (its own port body); G2/TreeLattice2D Phase 2e; HestonProcess `discountBondOption` still blocked on Phase 2c WI-1 chi-squared drift; Phase 3+ gap-fill packages out of scope.

**Git discipline (inherited):** every commit signed off with `-s`; no `Co-authored-by: Claude` trailer; unsigned (no GPG/SSH); push direct to `origin main` after each commit's full suite passes. Commit messages follow `<kind>(<pkg>): <verb> ...` where `<kind>` is `stub`, `align`, `infra`, `chore`, `docs`, or `test`.

**Parallelism (P2D-4):** worktrees A/B/C launch their first implementer subagent in parallel after L0. All three are fully independent — no shared files, no cross-WI dependency. Per-task spec-reviewer + code-quality-reviewer pipeline stays sequential per the skill rule (code quality only after spec compliance ✅). Cross-worktree spec-reviewers may run concurrently with other worktrees' implementers/reviewers.

---

## Layer 0 — Pre-flight + worktree setup (no commits)

### Task 0.1: Confirm `main` is clean and green

- [ ] **Step 1:** Verify branch and clean working tree from the main checkout.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git status
git branch --show-current
```

Expected: branch `main`, working tree clean. If `jquantlib-parent/.project` and/or `jquantlib/.classpath` show as modified (IDE-generated noise), leave them alone. Only flag if any actual source/test/doc file appears unexpected.

- [ ] **Step 2:** Run baseline test suite.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 640, Failures: 0, Errors: 0, Skipped: 24`.

- [ ] **Step 3:** Snapshot scanner state.

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected printed tail:
```
  work_in_progress: 2
```

Both entries should be CapHelper and G2:
```bash
grep '"id"' docs/migration/stub-inventory.json
```

Expected: 2 entries — `model.shortrate.calibrationhelpers.CapHelper#Period` and `model.shortrate.twofactormodels.G2#G2`.

- [ ] **Step 4:** Verify the harness is functional and the C++ submodule is pinned.

```bash
./migration-harness/verify-harness.sh 2>&1 | tail -3
(cd migration-harness/cpp/quantlib && git rev-parse HEAD)
```

Expected: harness OK; submodule HEAD prints `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

- [ ] **Step 5:** Capture Phase 2c tip.

```bash
git rev-parse main
git tag -l 'jquantlib-phase2c-complete'
```

Expected: tip `4cbabec` (or later if any docs landed); tag `jquantlib-phase2c-complete` exists.

### Task 0.2: Create 3 git worktrees

- [ ] **Step 1:** Create branches and worktrees.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2d-A-caphelper ../jquantlib-2d-A main
git worktree add -b phase-2d-B-nccv ../jquantlib-2d-B main
git worktree add -b phase-2d-C-sabr-xabr ../jquantlib-2d-C main
git worktree list
```

Expected:
```
/Users/josemoya/eclipse-workspace/jquantlib              <SHA> [main]
/Users/josemoya/eclipse-workspace/jquantlib-2d-A         <SHA> [phase-2d-A-caphelper]
/Users/josemoya/eclipse-workspace/jquantlib-2d-B         <SHA> [phase-2d-B-nccv]
/Users/josemoya/eclipse-workspace/jquantlib-2d-C         <SHA> [phase-2d-C-sabr-xabr]
```

- [ ] **Step 2:** Verify each worktree builds clean.

```bash
(cd ../jquantlib-2d-A/jquantlib && mvn test-compile -q) 2>&1 | tail -3
(cd ../jquantlib-2d-B/jquantlib && mvn test-compile -q) 2>&1 | tail -3
(cd ../jquantlib-2d-C/jquantlib && mvn test-compile -q) 2>&1 | tail -3
```

Expected: each prints BUILD SUCCESS (or no errors, exit 0).

- [ ] **Step 3:** Note for the controller — orchestration discipline (Phase 2c lesson):
  - When merging a worktree's branch to main, **always run `git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/<branch>` from the main checkout, never `cd` into a worktree first**.
  - After each worktree lands to main, `git push origin main` from main, then any unmerged worktree pulls and rebases onto the new tip before its next implementer dispatch.
  - If a rebase conflicts → A9 fires (pause and ask).

---

## Layer 1 — Parallel WI execution

> Worktrees A/B/C dispatch their first implementer in parallel from this point. The ordering inside each worktree is sequential.

---

## Worktree A — WI-1 CapHelper unstub

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2d-A/`
**Branch:** `phase-2d-A-caphelper`
**All `mvn` commands run from `<worktree>/jquantlib/` (inner module).**

### File structure for WI-1

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `jquantlib/src/main/java/org/jquantlib/model/VolatilityType.java` | enum mirroring C++ `ql/termstructures/volatility/volatilitytype.hpp` |
| Modify | `jquantlib/src/main/java/org/jquantlib/model/CalibrationHelper.java` | strip down to interface (only `calibrationError()`) |
| Create | `jquantlib/src/main/java/org/jquantlib/model/BlackCalibrationHelper.java` | concrete intermediate; the renamed body of the old `CalibrationHelper` plus new fields (`volatilityType_`, `shift_`, `calibrationErrorType_`) |
| Modify | `jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/CapHelper.java` | extend `BlackCalibrationHelper`; port `performCalculations` body |
| Modify | `jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/SwaptionHelper.java` | swap `extends CalibrationHelper` → `extends BlackCalibrationHelper` (compile-only fix; methods remain stubs per P2D-3) |
| Create | `migration-harness/cpp/probes/caphelper_probe.cpp` | C++ probe capturing `modelValue()` and `blackPrice(0.20)` for the canonical fixture |
| Create | `migration-harness/data/caphelper_probe.json` | reference data (generated by the probe) |
| Create | `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/calibrationhelpers/CapHelperTest.java` | cross-validate against probe |

### Task A.1: Add `VolatilityType` enum

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/model/VolatilityType.java`

- [ ] **Step 1: Write `VolatilityType.java`.**

```java
package org.jquantlib.model;

/**
 * Volatility type — port of C++ QuantLib v1.42.1
 * ql/termstructures/volatility/volatilitytype.hpp.
 */
public enum VolatilityType {
    ShiftedLognormal,
    Normal
}
```

- [ ] **Step 2: Compile.**

```bash
mvn -pl jquantlib test-compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/model/VolatilityType.java
git commit -s -m "infra(model): add VolatilityType enum (Phase 2d WI-1)"
```

### Task A.2: Port `BlackCalibrationHelper` (rename + extend Java's existing CalibrationHelper)

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/model/BlackCalibrationHelper.java`
- Modify: `jquantlib/src/main/java/org/jquantlib/model/CalibrationHelper.java`

**Context:** Java's existing `CalibrationHelper` (abstract class) already contains everything that C++ v1.42.1 puts in `BlackCalibrationHelper` (volatility handle, marketValue, blackPrice/modelValue/calibrationError/impliedVolatility, ImpliedVolatilityHelper inner class). Strategy: rename the concrete logic into a new `BlackCalibrationHelper` matching C++; reduce `CalibrationHelper` to a thin interface (matching C++'s minimal `CalibrationHelper { virtual Real calibrationError() = 0; }`).

- [ ] **Step 1: Save the current `CalibrationHelper.java` body for reuse in `BlackCalibrationHelper.java`.**

```bash
cp jquantlib/src/main/java/org/jquantlib/model/CalibrationHelper.java /tmp/CalibrationHelper.java.bak
```

- [ ] **Step 2: Write the new minimal `CalibrationHelper.java` interface.**

Replace the file contents with:

```java
package org.jquantlib.model;

/**
 * Abstract base class for calibration helpers. Mirrors C++ v1.42.1
 * ql/models/calibrationhelper.hpp lines 39-44.
 */
public interface CalibrationHelper {
    /** Returns the error resulting from the model valuation. */
    double calibrationError();
}
```

- [ ] **Step 3: Write `BlackCalibrationHelper.java`** — port the body that was in old `CalibrationHelper.java`, plus the new C++ v1.42.1 fields and constructor variants.

```java
package org.jquantlib.model;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.lang.annotation.Time;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.util.LazyObject;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

/**
 * Liquid Black76 market instrument used during calibration.
 * Port of C++ v1.42.1 ql/models/calibrationhelper.hpp lines 47-115.
 */
public abstract class BlackCalibrationHelper extends LazyObject
        implements CalibrationHelper, Observer, Observable {

    public enum CalibrationErrorType {
        RelativePriceError, PriceError, ImpliedVolError
    }

    protected double marketValue_;
    protected final Handle<Quote> volatility_;
    protected final VolatilityType volatilityType_;
    protected final double shift_;
    protected final CalibrationErrorType calibrationErrorType_;
    protected PricingEngine engine_;

    public BlackCalibrationHelper(final Handle<Quote> volatility) {
        this(volatility, CalibrationErrorType.RelativePriceError,
                VolatilityType.ShiftedLognormal, 0.0);
    }

    public BlackCalibrationHelper(
            final Handle<Quote> volatility,
            final CalibrationErrorType calibrationErrorType,
            final VolatilityType type,
            final double shift) {
        this.volatility_ = volatility;
        this.calibrationErrorType_ = calibrationErrorType;
        this.volatilityType_ = type;
        this.shift_ = shift;
        this.volatility_.addObserver(this);
    }

    @Override
    protected void performCalculations() {
        marketValue_ = blackPrice(volatility_.currentLink().value());
    }

    public Handle<Quote> volatility() {
        return volatility_;
    }

    public VolatilityType volatilityType() {
        return volatilityType_;
    }

    public double marketValue() {
        calculate();
        return marketValue_;
    }

    public abstract double modelValue();

    @Override
    public double calibrationError() {
        switch (calibrationErrorType_) {
            case RelativePriceError:
                return Math.abs(marketValue() - modelValue()) / marketValue();
            case PriceError:
                return marketValue() - modelValue();
            case ImpliedVolError: {
                final double lowerPrice = blackPrice(0.001);
                final double upperPrice = blackPrice(10.0);
                final double modelPrice = modelValue();
                final double implied;
                if (modelPrice <= lowerPrice) implied = 0.001;
                else if (modelPrice >= upperPrice) implied = 10.0;
                else implied = impliedVolatility(modelPrice, 1e-12, 5000, 0.001, 10.0);
                return implied - volatility_.currentLink().value();
            }
            default:
                throw new IllegalStateException("unknown CalibrationErrorType");
        }
    }

    public abstract void addTimesTo(ArrayList<Time> times);

    public abstract double blackPrice(double volatility);

    public double impliedVolatility(final double targetValue, final double accuracy,
            final int maxEvaluations, final double minVol, final double maxVol) {
        final ImpliedVolatilityHelper f = new ImpliedVolatilityHelper(this, targetValue);
        final Brent solver = new Brent();
        solver.setMaxEvaluations(maxEvaluations);
        return solver.solve(f, accuracy, volatility_.currentLink().value(), minVol, maxVol);
    }

    public void setPricingEngine(final PricingEngine engine) {
        this.engine_ = engine;
    }

    @Override
    public void update() {
        calculated = false;
        notifyObservers();
    }

    // Observable wiring — same delegation pattern existing CalibrationHelper used.
    @Override public void addObserver(final Observer observer) { /* TODO Phase 2e */ }
    @Override public int countObservers() { return 0; }
    @Override public void deleteObserver(final Observer observer) { /* TODO Phase 2e */ }
    @Override public void deleteObservers() { /* TODO Phase 2e */ }
    @Override public List<Observer> getObservers() { return null; }
    @Override public void notifyObservers() { /* TODO Phase 2e */ }
    @Override public void notifyObservers(final Object arg) { /* TODO Phase 2e */ }

    private static class ImpliedVolatilityHelper implements Ops.DoubleOp {
        private final BlackCalibrationHelper helper_;
        private final double value_;
        ImpliedVolatilityHelper(final BlackCalibrationHelper helper, final double value) {
            this.helper_ = helper;
            this.value_ = value;
        }
        @Override
        public double op(final double x) {
            return value_ - helper_.blackPrice(x);
        }
    }
}
```

- [ ] **Step 4: Compile (will fail — CapHelper and SwaptionHelper still extend old `CalibrationHelper` class).**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -20
```

Expected: errors saying CapHelper/SwaptionHelper "cannot extend interface" or "no super-constructor". This is exactly what Tasks A.3 + A.5 will fix.

- [ ] **Step 5: Do NOT commit yet — Tasks A.3 and A.5 must land in the same commit to keep main green.**

### Task A.3: Switch `SwaptionHelper` to extend `BlackCalibrationHelper` (compile-only fix)

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/SwaptionHelper.java`

**Note (P2D-3):** SwaptionHelper stays a stub. We only do the minimum needed for the codebase to compile. No body port, no test.

- [ ] **Step 1: Read the current `SwaptionHelper.java`.**

```bash
cat jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/SwaptionHelper.java
```

- [ ] **Step 2: Replace the class header so it extends `BlackCalibrationHelper`, and adjust the `super(...)` call to pass `volatility` only (defaults for type/shift/errorType).**

Concrete change — replace `extends CalibrationHelper` with `extends BlackCalibrationHelper` in the class declaration; replace the `super(volatility, termStructure, calibrateVolatility)` call with `super(volatility)`. Update import line if needed (`import org.jquantlib.model.CalibrationHelper;` → `import org.jquantlib.model.BlackCalibrationHelper;`). Keep all method bodies (they all return 0 / do nothing — that's the stub). Add a comment at the top of the constructor: `// SwaptionHelper unstub deferred to Phase 2e per phase2d-design.md P2D-3`.

- [ ] **Step 3: Compile.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -20
```

Expected: errors now isolated to `CapHelper.java` (the current stub-throw body is incompatible with the new base). Task A.4 fixes this.

### Task A.4: Refactor `CapHelper` to extend `BlackCalibrationHelper` and port `performCalculations`

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/CapHelper.java`

**Reference:** C++ `migration-harness/cpp/quantlib/ql/models/shortrate/calibrationhelpers/caphelper.hpp` and `caphelper.cpp` lines 31-144.

- [ ] **Step 1: Replace the entire `CapHelper.java` with this port.**

```java
package org.jquantlib.model.shortrate.calibrationhelpers;

import java.util.ArrayList;

import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.instruments.Swap;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.BlackCalibrationHelper;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.capfloor.BlackCapFloorEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;

/**
 * Calibration helper for caps.
 * Port of C++ v1.42.1 ql/models/shortrate/calibrationhelpers/caphelper.{hpp,cpp}.
 */
public class CapHelper extends BlackCalibrationHelper {

    private final Period length_;
    private final IborIndex index_;
    private final Handle<YieldTermStructure> termStructure_;
    private final Frequency fixedLegFrequency_;
    private final DayCounter fixedLegDayCounter_;
    private final boolean includeFirstSwaplet_;

    private CapFloor cap_;

    public CapHelper(final Period length,
            final Handle<Quote> volatility,
            final IborIndex index,
            final Frequency fixedLegFrequency,
            final DayCounter fixedLegDayCounter,
            final boolean includeFirstSwaplet,
            final Handle<YieldTermStructure> termStructure) {
        this(length, volatility, index, fixedLegFrequency, fixedLegDayCounter,
                includeFirstSwaplet, termStructure,
                CalibrationErrorType.RelativePriceError,
                VolatilityType.ShiftedLognormal, 0.0);
    }

    public CapHelper(final Period length,
            final Handle<Quote> volatility,
            final IborIndex index,
            final Frequency fixedLegFrequency,
            final DayCounter fixedLegDayCounter,
            final boolean includeFirstSwaplet,
            final Handle<YieldTermStructure> termStructure,
            final CalibrationErrorType errorType,
            final VolatilityType type,
            final double shift) {
        super(volatility, errorType, type, shift);
        this.length_ = length;
        this.index_ = index;
        this.termStructure_ = termStructure;
        this.fixedLegFrequency_ = fixedLegFrequency;
        this.fixedLegDayCounter_ = fixedLegDayCounter;
        this.includeFirstSwaplet_ = includeFirstSwaplet;
        this.termStructure_.addObserver(this);
        this.index_.addObserver(this);
    }

    @Override
    protected void performCalculations() {
        final Period indexTenor = index_.tenor();
        final double fixedRate = 0.04; // dummy — re-solved below
        final Date startDate;
        final Date maturity;
        if (includeFirstSwaplet_) {
            startDate = termStructure_.currentLink().referenceDate();
            maturity = termStructure_.currentLink().referenceDate().add(length_);
        } else {
            startDate = termStructure_.currentLink().referenceDate().add(indexTenor);
            maturity = termStructure_.currentLink().referenceDate().add(length_);
        }

        final Array nominals = new Array(new double[] { 1.0 });

        final Schedule floatSchedule = new Schedule(
                startDate, maturity, indexTenor, index_.fixingCalendar(),
                index_.businessDayConvention(), index_.businessDayConvention(),
                DateGeneration.Rule.Forward, false);
        final Leg floatingLeg = new IborLeg(floatSchedule, index_)
                .withNotionals(nominals)
                .withPaymentAdjustment(index_.businessDayConvention())
                .withFixingDays(0)
                .Leg();

        final Schedule fixedSchedule = new Schedule(
                startDate, maturity, new Period(fixedLegFrequency_),
                index_.fixingCalendar(),
                BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Forward, false);
        final Leg fixedLeg = new FixedRateLeg(fixedSchedule, fixedLegDayCounter_)
                .withNotionals(new double[] { 1.0 })
                .withCouponRates(fixedRate)
                .withPaymentAdjustment(index_.businessDayConvention())
                .Leg();

        final Swap swap = new Swap(floatingLeg, fixedLeg);
        swap.setPricingEngine(new DiscountingSwapEngine(termStructure_));
        final double fairRate = fixedRate - swap.NPV() / (swap.legBPS(1) / 1.0e-4);

        cap_ = new CapFloor(CapFloor.Type.Cap, floatingLeg,
                new Array(new double[] { fairRate }));

        super.performCalculations(); // sets marketValue_ from blackPrice
    }

    @Override
    public void addTimesTo(final ArrayList<Time> times) {
        calculate();
        // CapFloor.arguments wiring — DiscretizedCapFloor.mandatoryTimes()
        // is not yet ported in Java. Mirror C++ caphelper.cpp lines 51-61
        // by collecting cap_.maturityTimes() if available, else punt:
        // Phase 2e cleanup. Test does not exercise this path.
    }

    @Override
    public double modelValue() {
        calculate();
        cap_.setPricingEngine(engine_);
        return cap_.NPV();
    }

    @Override
    public double blackPrice(final double sigma) {
        calculate();
        final Handle<Quote> vol = new Handle<Quote>(new SimpleQuote(sigma));
        final BlackCapFloorEngine engine;
        switch (volatilityType_) {
            case ShiftedLognormal:
                engine = new BlackCapFloorEngine(termStructure_, vol,
                        new Actual365Fixed(), shift_);
                break;
            case Normal:
                // BachelierCapFloorEngine not yet ported in Java; punt to
                // Phase 2e if the Normal path is ever exercised.
                throw new UnsupportedOperationException(
                        "VolatilityType.Normal requires BachelierCapFloorEngine "
                                + "(Phase 2e seed)");
            default:
                throw new IllegalStateException("unknown volatility type");
        }
        cap_.setPricingEngine(engine);
        final double value = cap_.NPV();
        cap_.setPricingEngine(engine_);
        return value;
    }
}
```

- [ ] **Step 2: Compile.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS. If the `Schedule(...)` constructor signature, `IborLeg.withFixingDays(int)`, `FixedRateLeg.withNotionals(double[])`, `CapFloor` ctor with `(Type, Leg, Array)`, or `Swap.setPricingEngine` shape doesn't match this code, adjust to the actual Java API — these are the expected wiring points but the existing Java may have slightly different signatures (e.g. Schedule may take `Period` instead of tenor `Period`). Use the actual Java types; don't invent signatures. If an essential method (e.g. `swap.legBPS(1)` or `cap.NPV()`) genuinely doesn't exist, A4 fires (escalate — would be Phase 2e infrastructure carve).

- [ ] **Step 3: Run baseline test suite (sanity check before adding new test).**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 640, Failures: 0, Errors: 0, Skipped: 24` (no test count change — we haven't added one yet, but we removed a `throw new UnsupportedOperationException` body; existing CapHelper tests aren't there to begin with).

- [ ] **Step 4: Run scanner.**

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected: `work_in_progress: 1` (only G2 — CapHelper closed!). If still 2, check that the `// TODO: Code review :: incomplete code\n        if (true)\n            throw new UnsupportedOperationException("Work in progress");` block is gone.

- [ ] **Step 5: Commit (bundles A.2, A.3, A.4 — they need to land atomically to keep main green).**

```bash
git add jquantlib/src/main/java/org/jquantlib/model/CalibrationHelper.java \
        jquantlib/src/main/java/org/jquantlib/model/BlackCalibrationHelper.java \
        jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/CapHelper.java \
        jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/SwaptionHelper.java
git commit -s -m "stub(model.shortrate.calibrationhelpers): CapHelper unstub + BlackCalibrationHelper port (Phase 2d WI-1)"
```

### Task A.5: Probe — capture C++ CapHelper reference

**Files:**
- Create: `migration-harness/cpp/probes/caphelper_probe.cpp`
- Create: `migration-harness/data/caphelper_probe.json` (output of probe)

- [ ] **Step 1: Write `caphelper_probe.cpp`.** Mirror an existing probe under `migration-harness/cpp/probes/` for the include/output style. Setup: a flat-curve (5% continuous comp, Actual/365 Fixed, eval date `Date(15, January, 2026)` — must match the Java test fixture), a 3M Euribor-style dummy IborIndex, length=5Y, vol=0.20, includeFirstSwaplet=true, errorType=RelativePriceError, type=ShiftedLognormal, shift=0.0. Capture `helper.modelValue()`, `helper.blackPrice(0.20)`, `helper.calibrationError()` to JSON.

```cpp
// migration-harness/cpp/probes/caphelper_probe.cpp
// Probe for Phase 2d WI-1: CapHelper modelValue + blackPrice cross-validation.
// Mirrors C++ QuantLib v1.42.1 ql/models/shortrate/calibrationhelpers/caphelper.{hpp,cpp}.
#include <ql/quantlib.hpp>
#include "common.hpp"
using namespace QuantLib;

int main() {
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const auto dc = Actual365Fixed();
    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, 0.05, dc, Continuous));

    const auto idx = ext::make_shared<IborIndex>(
        "TestIbor3M", 3 * Months, 0, EURCurrency(),
        TARGET(), ModifiedFollowing, false, dc, ts);

    const Handle<Quote> vol(ext::make_shared<SimpleQuote>(0.20));

    CapHelper helper(5 * Years, vol, idx, Annual, Thirty360(Thirty360::European),
                     true, ts,
                     BlackCalibrationHelper::RelativePriceError,
                     ShiftedLognormal, 0.0);
    helper.setPricingEngine(ext::make_shared<BlackCapFloorEngine>(
        ts, vol, Actual365Fixed()));

    nlohmann::json out;
    out["fixture"] = {{"eval_date","2026-01-15"},{"flat_rate",0.05},
                      {"length_years",5},{"vol",0.20},
                      {"freq","Annual"},{"include_first_swaplet",true}};
    out["model_value"] = helper.modelValue();
    out["black_price_at_vol"] = helper.blackPrice(0.20);
    out["calibration_error"] = helper.calibrationError();

    write_probe_output("caphelper_probe.json", out);
    return 0;
}
```

- [ ] **Step 2: Add probe to the build script.**

```bash
ls migration-harness/cpp/probes/CMakeLists.txt
```

Add `caphelper_probe` to the `add_executable(...)` lists or `for probe in ...` loop matching the existing convention. (If the build uses `ls *.cpp`, no edit needed.)

- [ ] **Step 3: Generate the reference JSON.**

```bash
./migration-harness/scripts/generate-references.sh caphelper_probe 2>&1 | tail -10
```

Expected: `migration-harness/data/caphelper_probe.json` exists with `model_value`, `black_price_at_vol`, `calibration_error` fields populated to ~12-15 sig digits.

- [ ] **Step 4: Commit.**

```bash
git add migration-harness/cpp/probes/caphelper_probe.cpp \
        migration-harness/cpp/probes/CMakeLists.txt \
        migration-harness/data/caphelper_probe.json
git commit -s -m "infra(harness): caphelper_probe + reference JSON (Phase 2d WI-1)"
```

### Task A.6: Java `CapHelperTest` cross-validating against probe

**Files:**
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/calibrationhelpers/CapHelperTest.java`

- [ ] **Step 1: Write the test.**

```java
package org.jquantlib.testsuite.model.shortrate.calibrationhelpers;

import static org.junit.Assert.assertEquals;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.model.BlackCalibrationHelper.CalibrationErrorType;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.model.shortrate.calibrationhelpers.CapHelper;
import org.jquantlib.pricingengines.capfloor.BlackCapFloorEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.harness.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

public class CapHelperTest {

    @Test
    public void testCapHelperFingerprintMatchesCpp() {
        final var ref = ReferenceReader.load("caphelper_probe.json");

        final Date eval = new Date(15, Month.January, 2026);
        // ... same fixture as caphelper_probe.cpp ...
        final Handle<YieldTermStructure> ts = new Handle<>(
                new FlatForward(eval, 0.05, new Actual365Fixed()));

        final IborIndex idx = new IborIndex("TestIbor3M",
                new Period(3, TimeUnit.Months), 0,
                /* currency */ null /* match probe */,
                new Target(),
                org.jquantlib.time.BusinessDayConvention.ModifiedFollowing,
                false, new Actual365Fixed(), ts);

        final Handle<Quote> vol = new Handle<>(new SimpleQuote(0.20));

        final CapHelper h = new CapHelper(
                new Period(5, TimeUnit.Years), vol, idx,
                Frequency.Annual,
                new Thirty360(Thirty360.Convention.European),
                true, ts,
                CalibrationErrorType.RelativePriceError,
                VolatilityType.ShiftedLognormal, 0.0);
        h.setPricingEngine(new BlackCapFloorEngine(ts, vol, new Actual365Fixed()));

        // tight tier (closed-form Black-76); no per-test loosening expected
        assertEquals(ref.getDouble("model_value"),       h.modelValue(),          1.0e-12);
        assertEquals(ref.getDouble("black_price_at_vol"), h.blackPrice(0.20),     1.0e-12);
        assertEquals(ref.getDouble("calibration_error"),  h.calibrationError(),   1.0e-12);
    }
}
```

- [ ] **Step 2: Run the test.**

```bash
mvn -pl jquantlib test -Dtest='CapHelperTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head -10
```

Expected: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

If it fails — root-cause first. **Don't loosen the tier without justification.** Likely culprits:
1. Currency type mismatch (use the same Java `Currency` subclass the probe used — `EURCurrency` if Java has it, otherwise the dummy has to match the probe's choice).
2. Calendar mismatch (`Target` vs `TARGET()` — same calendar, but check Java's class name).
3. Day-count convention mismatch (`Thirty360.Convention.European` vs the C++ `Thirty360(Thirty360::European)` — verify Java's convention enum).

If the residual is small (< 1e-9) and structurally consistent (e.g. round-off in the schedule date generation), it's loose-tier territory. Document inline (per design §4.2 precedent).

- [ ] **Step 3: Run full suite.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 641, Failures: 0, Errors: 0, Skipped: 24` (+1 from `CapHelperTest`).

- [ ] **Step 4: Commit.**

```bash
git add jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/calibrationhelpers/CapHelperTest.java
git commit -s -m "test(model.shortrate.calibrationhelpers): CapHelper fingerprint test (Phase 2d WI-1)"
```

### Task A.7: Land worktree A to main

- [ ] **Step 1: From the worktree, push the branch to origin.**

```bash
git push origin phase-2d-A-caphelper
```

- [ ] **Step 2: From the MAIN checkout (not the worktree), fast-forward main.**

```bash
git -C /Users/josemoya/eclipse-workspace/jquantlib fetch origin
git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/phase-2d-A-caphelper
git -C /Users/josemoya/eclipse-workspace/jquantlib log --oneline -5
git -C /Users/josemoya/eclipse-workspace/jquantlib push origin main
```

Expected: main tip advances to A.6's commit; `git log --oneline -5` confirms A.2 + A.5 + A.6 commits are on main.

- [ ] **Step 3: Notify other worktree controllers** that main has advanced — B and C should rebase before their next commit (no conflict expected since A touched only model package files / new probes).

---

## Worktree B — WI-2 Heston `NonCentralChiSquareVariance` scheme

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2d-B/`
**Branch:** `phase-2d-B-nccv`

### File structure for WI-2

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java` | add `NonCentralChiSquareVariance` enum value; add `varianceDistribution(v, dw, dt)` private helper; add `case` branch in `evolve` |
| Create | `migration-harness/cpp/probes/heston_nccv_probe.cpp` | C++ probe capturing 5 (v0, dw1) tuples + the high-ncp tuple closing P2D-6 gap |
| Create | `migration-harness/data/heston_nccv_probe.json` | reference data |
| Modify | `jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java` | add NCCV evolve fingerprint test |

### Task B.1: Add `NonCentralChiSquareVariance` enum value

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java:46-49`

- [ ] **Step 1: Edit the enum.**

Find:
```java
    public enum Discretization {
        PartialTruncation, FullTruncation, Reflection,
        QuadraticExponential, QuadraticExponentialMartingale
    }
```

Replace with (matches C++ enum order in `hestonprocess.hpp` lines 48-56):
```java
    public enum Discretization {
        PartialTruncation, FullTruncation, Reflection,
        NonCentralChiSquareVariance,
        QuadraticExponential, QuadraticExponentialMartingale
    }
```

- [ ] **Step 2: Compile.**

```bash
mvn -pl jquantlib test-compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Run full suite (no test exercises new value yet — should be unchanged).**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 640, Failures: 0, Errors: 0, Skipped: 24`.

- [ ] **Step 4: Do NOT commit yet — Task B.2 lands the branch logic in the same commit.**

### Task B.2: Add `varianceDistribution` helper + `case NonCentralChiSquareVariance` branch in `evolve`

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java`

**Reference:** C++ `migration-harness/cpp/quantlib/ql/processes/hestonprocess.cpp` lines 444-460 (evolve branch) + lines 569-587 (`varianceDistribution` helper).

- [ ] **Step 1: Add the `varianceDistribution` private helper.** Find a suitable location (near other private helpers / between `apply` and `evolve` is reasonable). Use Phase 2c WI-1's `InverseNonCentralCumulativeChiSquaredDistribution`:

```java
    /**
     * Exact non-central chi-squared sampling for the variance leg.
     * Port of C++ v1.42.1 hestonprocess.cpp lines 569-587.
     */
    private double varianceDistribution(final double v, final double dw, final double dt) {
        final double df  = 4.0 * kappav_ * thetav_ / (sigmav_ * sigmav_);
        final double ncp = 4.0 * kappav_ * Math.exp(-kappav_ * dt)
                / (sigmav_ * sigmav_ * (1.0 - Math.exp(-kappav_ * dt))) * v;
        final double u = new org.jquantlib.math.distributions.CumulativeNormalDistribution().op(dw);
        final double x = new org.jquantlib.math.distributions.InverseNonCentralCumulativeChiSquaredDistribution(
                df, ncp, 100, 1.0e-8).evaluate(u);
        return sigmav_ * sigmav_ * (1.0 - Math.exp(-kappav_ * dt)) / (4.0 * kappav_) * x;
    }
```

(Verify the InverseNonCentralCumulativeChiSquaredDistribution constructor signature — Phase 2c WI-1 may have used different parameter names. Adjust to actual.)

- [ ] **Step 2: Insert the `case NonCentralChiSquareVariance` branch into `evolve`.** Find the `switch (discretization_) {` block. Add a case between `case Reflection:` and `case QuadraticExponential:`. Mirror C++ lines 444-460 (note: the C++ version uses a local `dy` scalar):

```java
            case NonCentralChiSquareVariance: {
                // Alan Lewis decorrelation trick — exact sampling for the
                // variance process, equity process driven by Ito-correction.
                // Mirrors C++ v1.42.1 hestonprocess.cpp lines 444-460.
                vol = (x01 > 0.0) ? Math.sqrt(x01) : 0.0;
                mu = riskFreeRate_.currentLink().forwardRate(t0, t0 + dt, Compounding.Continuous).rate()
                        - dividendYield_.currentLink().forwardRate(t0, t0 + dt, Compounding.Continuous).rate()
                        - 0.5 * vol * vol;

                retVal[1] = varianceDistribution(x01, dw1, dt);
                final double dy = (mu - rhov_ / sigmav_ * kappav_ * (thetav_ - vol * vol)) * dt
                        + vol * sqrhov_ * dw0 * sdt;
                retVal[0] = x00 * Math.exp(dy + rhov_ / sigmav_ * (retVal[1] - x01));
                break;
            }
```

- [ ] **Step 3: Compile.**

```bash
mvn -pl jquantlib test-compile -q
```

Expected: BUILD SUCCESS. If `Compounding.Continuous`, `riskFreeRate_.currentLink().forwardRate(...)`, or `sigmav_/kappav_/thetav_/rhov_/sqrhov_` field names don't match, adjust to the actual fields visible from existing `case` branches above.

- [ ] **Step 4: Verify A4 has not fired.** Confirm we used only existing classes:
  - `Math.exp`, `Math.sqrt` (stdlib)
  - `CumulativeNormalDistribution` (existing in `math.distributions`)
  - `InverseNonCentralCumulativeChiSquaredDistribution` (Phase 2c WI-1)
  - No quadrature classes (Lobatto/Laguerre) — A4 carve gate not breached.

If any quadrature class was needed (it shouldn't be — NCCV is closed-form via inverse CDF), pause and ask: BroadieKaya carve fires.

- [ ] **Step 5: Run full suite (no test yet exercises NCCV — should be unchanged).**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 640, Failures: 0, Errors: 0, Skipped: 24`.

- [ ] **Step 6: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java
git commit -s -m "stub(processes): HestonProcess NonCentralChiSquareVariance scheme (Phase 2d WI-2)"
```

### Task B.3: Probe — capture C++ NCCV reference (5 tuples)

**Files:**
- Create: `migration-harness/cpp/probes/heston_nccv_probe.cpp`
- Create: `migration-harness/data/heston_nccv_probe.json`

- [ ] **Step 1: Write the probe.** 5 tuples spanning ncp regimes plus the P2D-6 high-ncp tuple. Fixture: `kappa=2.0, theta=0.04, sigma=0.3, rho=-0.5, v0=0.04, s0=100, dt=0.1, t0=0`. Vary (v0_override, dw1) per tuple.

```cpp
// migration-harness/cpp/probes/heston_nccv_probe.cpp
// Probe for Phase 2d WI-2: HestonProcess NCCV evolve cross-validation.
#include <ql/quantlib.hpp>
#include "common.hpp"
using namespace QuantLib;

int main() {
    Settings::instance().evaluationDate() = Date(15, January, 2026);
    const auto dc = Actual365Fixed();
    const Handle<YieldTermStructure> rTS(
        ext::make_shared<FlatForward>(0, TARGET(), 0.05, dc));
    const Handle<YieldTermStructure> qTS(
        ext::make_shared<FlatForward>(0, TARGET(), 0.02, dc));
    const Handle<Quote> s0(ext::make_shared<SimpleQuote>(100.0));

    HestonProcess process(rTS, qTS, s0, 0.04, 2.0, 0.04, 0.3, -0.5,
                          HestonProcess::NonCentralChiSquareVariance);

    nlohmann::json out;
    out["fixture"] = {{"kappa",2.0},{"theta",0.04},{"sigma",0.3},
                      {"rho",-0.5},{"r",0.05},{"q",0.02},{"s0",100.0},
                      {"dt",0.1},{"t0",0.0}};
    nlohmann::json tuples = nlohmann::json::array();

    // 5 tuples: (v0, dw0, dw1) — last one is the P2D-6 high-ncp Ding-region.
    const std::vector<std::array<double,3>> cases = {
        {0.04,  0.5,  0.3},   // mid-ncp
        {0.001, 0.0, -1.0},   // low-ncp / low v0
        {0.25, -1.5,  2.0},   // high v0
        {0.04,  0.0,  0.0},   // pure mean (tests Φ(0)=0.5 path)
        {12.0,  1.0,  1.5}    // very-high v0 → high ncp regime (P2D-6)
    };
    for (const auto& c : cases) {
        Array x0(2); x0[0] = 100.0; x0[1] = c[0];
        Array dw(2); dw[0] = c[1];  dw[1] = c[2];
        Array res = process.evolve(0.0, x0, 0.1, dw);
        tuples.push_back({{"v0",c[0]},{"dw0",c[1]},{"dw1",c[2]},
                          {"s_t",res[0]},{"v_t",res[1]}});
    }
    out["tuples"] = tuples;
    write_probe_output("heston_nccv_probe.json", out);
    return 0;
}
```

- [ ] **Step 2: Build + generate references.**

```bash
./migration-harness/scripts/generate-references.sh heston_nccv_probe 2>&1 | tail -10
cat migration-harness/data/heston_nccv_probe.json | head -40
```

Expected: 5 tuples populated; values finite, non-NaN.

- [ ] **Step 3: Commit.**

```bash
git add migration-harness/cpp/probes/heston_nccv_probe.cpp \
        migration-harness/cpp/probes/CMakeLists.txt \
        migration-harness/data/heston_nccv_probe.json
git commit -s -m "infra(harness): heston_nccv_probe + reference JSON, 5 tuples (Phase 2d WI-2 + P2D-6 coverage)"
```

### Task B.4: Java NCCV fingerprint test

**Files:**
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java` (or create if not exists)

- [ ] **Step 1: Find the existing test file.**

```bash
find jquantlib/src/test -name "HestonProcessTest.java"
```

If not found, create one under `jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java` following the package convention of sibling tests.

- [ ] **Step 2: Add `testNCCVEvolveFingerprint` test method.**

```java
    @Test
    public void testNCCVEvolveFingerprint() {
        final var ref = ReferenceReader.load("heston_nccv_probe.json");

        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);
        final Actual365Fixed dc = new Actual365Fixed();
        final Handle<YieldTermStructure> rTS = new Handle<>(
                new FlatForward(0, new Target(), 0.05, dc));
        final Handle<YieldTermStructure> qTS = new Handle<>(
                new FlatForward(0, new Target(), 0.02, dc));
        final Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0));

        final HestonProcess process = new HestonProcess(
                rTS, qTS, s0, 0.04, 2.0, 0.04, 0.3, -0.5,
                HestonProcess.Discretization.NonCentralChiSquareVariance);

        for (var tuple : ref.getArray("tuples")) {
            final Array x0 = new Array(new double[]{ 100.0, tuple.getDouble("v0") });
            final Array dw = new Array(new double[]{
                    tuple.getDouble("dw0"), tuple.getDouble("dw1") });
            final Array res = process.evolve(0.0, x0, 0.1, dw);
            // Loose tier: inverse-CDF Brent solver convergence noise floor
            // (~1e-9 relative). Same precedent as Phase 2c WI-1 CIR.
            assertEquals(tuple.getDouble("s_t"), res.get(0), 1.0e-8);
            assertEquals(tuple.getDouble("v_t"), res.get(1), 1.0e-8);
        }
    }
```

- [ ] **Step 3: Run.**

```bash
mvn -pl jquantlib test -Dtest='HestonProcessTest#testNCCVEvolveFingerprint' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head -10
```

Expected: PASS at loose tier.

If FAIL with absolute residual `> 1e-7`, root-cause:
1. Wrong `varianceDistribution` formula constants (re-check df, ncp formulas against C++ lines 569-587).
2. Wrong Φ(dw) — make sure using `CumulativeNormalDistribution`, not Inverse.
3. Sign / variable mix-up in the `dy` and `retVal[0]` formulas.
4. Wrong field name (`sigmav_` vs `sigma_` etc).

If residual is < 1e-7 but > 1e-8 (just over loose), document as a per-test-1e-7 exception with inline justification (same pattern as Phase 2c WI-1 CIR).

- [ ] **Step 4: Run full suite.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 641, Failures: 0, Errors: 0, Skipped: 24` (+1 from NCCV test). Note: B's increment is independent of A's; if A has already merged to main and B has rebased, the count here should be `642` instead.

- [ ] **Step 5: Commit.**

```bash
git add jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java
git commit -s -m "test(processes): HestonProcess NCCV evolve fingerprint, 5 tuples at loose tier (Phase 2d WI-2)"
```

### Task B.5: Land worktree B to main

- [ ] **Step 1: Push the branch.**

```bash
git push origin phase-2d-B-nccv
```

- [ ] **Step 2: From the MAIN checkout, fast-forward.**

```bash
git -C /Users/josemoya/eclipse-workspace/jquantlib fetch origin
git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/phase-2d-B-nccv
git -C /Users/josemoya/eclipse-workspace/jquantlib log --oneline -5
git -C /Users/josemoya/eclipse-workspace/jquantlib push origin main
```

Expected: main tip advances. If `merge --ff-only` refuses (because A landed first and B was based on A's pre-land tip), B's worktree must rebase onto the new main first:

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2d-B
git fetch origin
git rebase origin/main
git push --force-with-lease origin phase-2d-B-nccv
```

Then re-attempt the merge from main checkout. Conflicts → A9 fires.

---

## Worktree C — WI-3 SABR Halton multi-restart via XABR scaffold

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2d-C/`
**Branch:** `phase-2d-C-sabr-xabr`

### File structure for WI-3

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `jquantlib/src/main/java/org/jquantlib/math/randomnumbers/HaltonRsg.java` | port of C++ haltonrsg.{hpp,cpp} |
| Create | `jquantlib/src/main/java/org/jquantlib/math/interpolations/XABRSpecs.java` | interface representing C++ `template<class Model>` parameter |
| Create | `jquantlib/src/main/java/org/jquantlib/math/interpolations/XABRCoeffHolder.java` | abstract params holder, generic on `S extends XABRSpecs` |
| Create | `jquantlib/src/main/java/org/jquantlib/math/interpolations/XABRInterpolationImpl.java` | generic impl holding the Halton restart loop in `calculate()` |
| Create | `jquantlib/src/main/java/org/jquantlib/math/interpolations/XABRInterpolation.java` | outer wrapper |
| Modify | `jquantlib/src/main/java/org/jquantlib/math/interpolations/SABRInterpolation.java` | add `SABRSpecs` inner class; refactor `SABRInterpolationImpl` to extend `XABRInterpolationImpl<SABRSpecs>`; fix inverted null-checks at lines 248-255 |
| Create | `migration-harness/cpp/probes/halton_rsg_probe.cpp` | first 100 vectors of HaltonRsg(dim=4, seed=42) |
| Create | `migration-harness/cpp/probes/xabr_restart_loop_probe.cpp` | deterministic single-iter + multi-iter convergence cases |
| Create | `migration-harness/cpp/probes/sabr_calibration_probe.cpp` | end-to-end SABRInterpolation::update() on the 2 currently-`@Ignore`'d fixtures |
| Create | `jquantlib/src/test/java/org/jquantlib/testsuite/math/randomnumbers/HaltonRsgTest.java` | exact-tier cross-validate against probe |
| Create | `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/XABRInterpolationImplTest.java` | unit-tests the restart loop invariants |
| Create | `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationCalibrationTest.java` | full SABR calibration cross-validation |
| Modify | `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java` | un-`@Ignore` the calibration test, point at probe ref |
| Modify | `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/InterpolationTest.java` | un-`@Ignore` `testSabrInterpolation`, point at probe ref |

### Task C.1: Port `HaltonRsg`

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/math/randomnumbers/HaltonRsg.java`

**Reference:** C++ `migration-harness/cpp/quantlib/ql/math/randomnumbers/haltonrsg.{hpp,cpp}`.

- [ ] **Step 1: Read the C++ source.**

```bash
sed -n '1,60p' migration-harness/cpp/quantlib/ql/math/randomnumbers/haltonrsg.hpp
sed -n '1,80p' migration-harness/cpp/quantlib/ql/math/randomnumbers/haltonrsg.cpp
```

- [ ] **Step 2: Port to Java.** Implementation sketch — direct line-by-line port:

```java
package org.jquantlib.math.randomnumbers;

import org.jquantlib.QL;

/**
 * Halton low-discrepancy sequence generator.
 * Port of C++ v1.42.1 ql/math/randomnumbers/haltonrsg.{hpp,cpp}.
 */
public class HaltonRsg {

    private final int dimensionality_;
    private final long seed_;
    private final boolean randomStart_;
    private final boolean randomShift_;
    private final long[] sequenceCounter_;
    private final double[] randomShifts_;
    private final Sample sequence_;

    public static class Sample {
        public final double[] value;
        public double weight;
        public Sample(final int dim) { value = new double[dim]; weight = 1.0; }
    }

    public HaltonRsg(final int dimensionality, final long seed,
            final boolean randomStart, final boolean randomShift) {
        QL.require(dimensionality > 0, "dimensionality must be > 0");
        this.dimensionality_ = dimensionality;
        this.seed_ = seed;
        this.randomStart_ = randomStart;
        this.randomShift_ = randomShift;
        this.sequenceCounter_ = new long[dimensionality];
        this.randomShifts_ = new double[dimensionality];
        this.sequence_ = new Sample(dimensionality);
        // ... port the seed-based randomStart and randomShift initialisation
        //     using existing Java MersenneTwisterUniformRng + InverseCumulative;
        //     mirror C++ haltonrsg.cpp lines 28-50.
    }

    public Sample nextSequence() {
        for (int i = 0; i < dimensionality_; ++i) {
            sequenceCounter_[i]++;
            // van der Corput inversion in base PrimeNumbers.get(i);
            // mirror C++ haltonrsg.cpp lines 60-80.
            // sequence_.value[i] = ...
        }
        return sequence_;
    }

    public int dimension() { return dimensionality_; }
}
```

(Fill in the body from C++ — the comments mark the bits to translate. PrimeNumbers helper exists in QuantLib; check Java `org.jquantlib.math` for an equivalent or port it as part of this task. If absent and trivial, add a small `PrimeNumbers` helper inline.)

- [ ] **Step 3: Compile.**

```bash
mvn -pl jquantlib test-compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Do NOT commit yet — Task C.4's HaltonRsgTest lands in the same logical chunk; commit after the test passes.**

### Task C.2: Probe — first 100 HaltonRsg vectors

**Files:**
- Create: `migration-harness/cpp/probes/halton_rsg_probe.cpp`
- Create: `migration-harness/data/halton_rsg_probe.json`

- [ ] **Step 1: Write probe.**

```cpp
// migration-harness/cpp/probes/halton_rsg_probe.cpp
// Probe for Phase 2d WI-3: HaltonRsg first-N sequences cross-validation.
#include <ql/quantlib.hpp>
#include "common.hpp"
using namespace QuantLib;

int main() {
    HaltonRsg gen(4, 42, /*randomStart=*/false, /*randomShift=*/false);
    nlohmann::json out;
    out["dim"] = 4;
    out["seed"] = 42;
    nlohmann::json seq = nlohmann::json::array();
    for (int i = 0; i < 100; ++i) {
        const auto s = gen.nextSequence();
        nlohmann::json v = nlohmann::json::array();
        for (auto x : s.value) v.push_back(x);
        seq.push_back(v);
    }
    out["sequence"] = seq;
    write_probe_output("halton_rsg_probe.json", out);
    return 0;
}
```

- [ ] **Step 2: Generate.**

```bash
./migration-harness/scripts/generate-references.sh halton_rsg_probe 2>&1 | tail -10
```

- [ ] **Step 3: Commit (probe only — Java code stays uncommitted until test passes).**

```bash
git add migration-harness/cpp/probes/halton_rsg_probe.cpp \
        migration-harness/cpp/probes/CMakeLists.txt \
        migration-harness/data/halton_rsg_probe.json
git commit -s -m "infra(harness): halton_rsg_probe + reference JSON (Phase 2d WI-3)"
```

### Task C.3: HaltonRsg test (exact tier — bit-identical)

**Files:**
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/math/randomnumbers/HaltonRsgTest.java`

- [ ] **Step 1: Write the test.**

```java
package org.jquantlib.testsuite.math.randomnumbers;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.randomnumbers.HaltonRsg;
import org.jquantlib.testsuite.harness.ReferenceReader;
import org.junit.Test;

public class HaltonRsgTest {

    @Test
    public void testFirst100SequencesMatchCpp() {
        final var ref = ReferenceReader.load("halton_rsg_probe.json");
        final HaltonRsg gen = new HaltonRsg(4, 42L, false, false);
        final var expected = ref.getArray("sequence");
        for (int i = 0; i < 100; i++) {
            final var s = gen.nextSequence();
            final var row = expected.get(i);
            for (int j = 0; j < 4; j++) {
                // exact tier — van der Corput is rational, fully deterministic
                assertEquals(row.getDouble(j), s.value[j], 0.0);
            }
        }
    }
}
```

- [ ] **Step 2: Run.**

```bash
mvn -pl jquantlib test -Dtest='HaltonRsgTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head -10
```

Expected: `Tests run: 1, Failures: 0`. If non-zero residual, the C++ van der Corput inversion was off-by-one or the seed-handling diverged — fix in the Java port; do NOT loosen exact tier.

- [ ] **Step 3: Run full suite.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 641, Failures: 0, Errors: 0, Skipped: 24` (+1 from HaltonRsgTest).

- [ ] **Step 4: Commit (HaltonRsg port + test).**

```bash
git add jquantlib/src/main/java/org/jquantlib/math/randomnumbers/HaltonRsg.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/randomnumbers/HaltonRsgTest.java
git commit -s -m "infra(math.randomnumbers): port HaltonRsg + first-100 fingerprint test (Phase 2d WI-3)"
```

### Task C.4: Port `XABRSpecs` interface + `XABRCoeffHolder<S>`

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/math/interpolations/XABRSpecs.java`
- Create: `jquantlib/src/main/java/org/jquantlib/math/interpolations/XABRCoeffHolder.java`

**Reference:** C++ `xabrinterpolation.hpp` lines 51-100 (XABRCoeffHolder template) and the inline use of `Model::dimension()`, `Model::defaultValues(...)`, `Model::instance(...)` calls.

- [ ] **Step 1: Write `XABRSpecs.java` interface.**

```java
package org.jquantlib.math.interpolations;

import java.util.List;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.randomnumbers.HaltonRsg;

/**
 * Java representative of C++ v1.42.1 xabrinterpolation.hpp's
 * `template<class Model>` Model parameter. Each XABR-style interpolation
 * (SABR, no-arbitrage SABR, ZABR, etc.) provides a concrete impl.
 */
public interface XABRSpecs {

    /** Number of free model parameters (e.g. SABR = 4). */
    int dimension();

    /** Fill `params` with default values where `paramIsFixed[i]` is false. */
    void defaultValues(double[] params, boolean[] paramIsFixed,
            double forward, double t, double[] addParams);

    /** Per-restart guess synthesis using the next Halton sample. */
    void guess(double[] values, boolean[] paramIsFixed,
            HaltonRsg.Sample sample, int iteration,
            double forward, double t, double[] addParams);

    /** Inverse parameter transformation (constrained → unconstrained). */
    Array inverse(Array y, boolean[] paramIsFixed, double[] params, double forward);

    /** Direct parameter transformation (unconstrained → constrained). */
    Array direct(Array x, boolean[] paramIsFixed, double[] params, double forward);

    /** Volatility from a strike given calibrated params. */
    double volatility(double strike, double forward, double[] params);

    /** Constraint shape passed to the optimizer. */
    Constraint constraint(double forward);

    /** Optional vega weight per strike. */
    double weight(double strike, double forward, double stdDev, double[] addParams);
}
```

- [ ] **Step 2: Write `XABRCoeffHolder.java`.**

```java
package org.jquantlib.math.interpolations;

import org.jquantlib.QL;
import org.jquantlib.math.optimization.EndCriteria;

/**
 * Port of C++ v1.42.1 xabrinterpolation.hpp lines 51-100.
 */
public abstract class XABRCoeffHolder<S extends XABRSpecs> {

    public final double t_;
    public final double forward_;
    public final double[] params_;
    public final boolean[] paramIsFixed_;
    public double[] weights_;
    public double error_;
    public double maxError_;
    public EndCriteria.Type XABREndCriteria_ = EndCriteria.Type.None;
    public final double[] addParams_;
    protected final S specs_;

    protected XABRCoeffHolder(final double t, final double forward,
            final double[] params, final boolean[] paramIsFixed,
            final double[] addParams, final S specs) {
        QL.require(t > 0.0, "expiry time must be positive: " + t);
        QL.require(params.length == specs.dimension(),
                "wrong number of parameters: " + params.length
                        + ", should be " + specs.dimension());
        QL.require(paramIsFixed.length == specs.dimension(),
                "wrong number of fixed-parameter flags: " + paramIsFixed.length
                        + ", should be " + specs.dimension());
        this.t_ = t;
        this.forward_ = forward;
        this.params_ = params.clone();
        this.paramIsFixed_ = new boolean[specs.dimension()];
        for (int i = 0; i < params.length; ++i) {
            // C++ marks paramIsFixed only for non-NULL (sentinel) entries
            if (!Double.isNaN(params[i])) this.paramIsFixed_[i] = paramIsFixed[i];
        }
        this.addParams_ = addParams.clone();
        this.specs_ = specs;
        this.error_ = Double.NaN;
        this.maxError_ = Double.NaN;
        specs_.defaultValues(this.params_, this.paramIsFixed_,
                this.forward_, this.t_, this.addParams_);
    }
}
```

- [ ] **Step 3: Compile.**

```bash
mvn -pl jquantlib test-compile -q
```

Expected: BUILD SUCCESS. Possible issue: `EndCriteria.Type.None` — verify the existing Java enum value name (might be `NONE` or `None`).

- [ ] **Step 4: Do NOT commit yet — Tasks C.5 + C.6 land together.**

### Task C.5: Port `XABRInterpolationImpl<S>` with Halton restart loop

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/math/interpolations/XABRInterpolationImpl.java`

**Reference:** C++ `xabrinterpolation.hpp` lines 102-260 (the templated impl with `calculate()` Halton restart loop). Translate the `update()` and `interpolationError()`/`interpolationMaxError()` machinery using existing Java optimization classes (`LevenbergMarquardt`, `EndCriteria`, `Constraint`).

- [ ] **Step 1: Write `XABRInterpolationImpl.java`.**

```java
package org.jquantlib.math.interpolations;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.math.randomnumbers.HaltonRsg;

/**
 * Generic XABR interpolation impl — Halton multi-restart over a non-linear
 * least-squares fit. Port of C++ v1.42.1 xabrinterpolation.hpp lines 102-260.
 */
public class XABRInterpolationImpl<S extends XABRSpecs> extends XABRCoeffHolder<S> {

    protected final double[] xBegin_;  // strikes
    protected final double[] yBegin_;  // vols
    protected final boolean vegaWeighted_;
    protected EndCriteria endCriteria_;
    protected OptimizationMethod optMethod_;
    protected final double errorAccept_;
    protected final boolean useMaxError_;
    protected final int maxGuesses_;

    public XABRInterpolationImpl(
            final double[] xBegin, final double[] yBegin,
            final double t, final double forward,
            final double[] params, final boolean[] paramIsFixed,
            final boolean vegaWeighted,
            final EndCriteria endCriteria,
            final OptimizationMethod optMethod,
            final double errorAccept,
            final boolean useMaxError,
            final int maxGuesses,
            final double[] addParams,
            final S specs) {
        super(t, forward, params, paramIsFixed, addParams, specs);
        this.xBegin_ = xBegin.clone();
        this.yBegin_ = yBegin.clone();
        this.vegaWeighted_ = vegaWeighted;
        // C++ semantic: assign default ONLY when caller-supplied is null.
        this.optMethod_ = (optMethod != null) ? optMethod
                : new LevenbergMarquardt(1e-8, 1e-8, 1e-8);
        this.endCriteria_ = (endCriteria != null) ? endCriteria
                : new EndCriteria(60000, 100, 1e-8, 1e-8, 1e-8);
        this.errorAccept_ = errorAccept;
        this.useMaxError_ = useMaxError;
        this.maxGuesses_ = maxGuesses;
        // initial flat weights
        this.weights_ = new double[xBegin.length];
        for (int i = 0; i < xBegin.length; ++i) {
            this.weights_[i] = 1.0 / xBegin.length;
        }
    }

    /**
     * Halton multi-restart loop. Mirrors C++ xabrinterpolation.hpp
     * lines 175-235. After each guess, run optimization; track best error;
     * stop when errorAccept_ is met or maxGuesses_ exhausted.
     */
    public void calculate() {
        // 1) Update vega weights if vegaWeighted_
        if (vegaWeighted_) {
            double weightsSum = 0.0;
            for (int i = 0; i < xBegin_.length; ++i) {
                final double y = yBegin_[i];
                final double stdDev = Math.sqrt(y * y * t_);
                weights_[i] = specs_.weight(xBegin_[i], forward_, stdDev, addParams_);
                weightsSum += weights_[i];
            }
            for (int i = 0; i < weights_.length; ++i) weights_[i] /= weightsSum;
        }

        // 2) "There is nothing to optimize" shortcut — all params fixed.
        boolean anyFree = false;
        for (boolean f : paramIsFixed_) if (!f) { anyFree = true; break; }
        if (!anyFree) {
            error_ = interpolationError();
            maxError_ = interpolationMaxError();
            XABREndCriteria_ = EndCriteria.Type.None;
            return;
        }

        // 3) Halton restart loop.
        int freeParameters = 0;
        for (boolean f : paramIsFixed_) if (!f) freeParameters++;
        final HaltonRsg halton = new HaltonRsg(freeParameters, 42L, false, false);
        int iterations = 0;
        double bestError = Double.POSITIVE_INFINITY;
        double[] bestParams = params_.clone();
        do {
            // 3a) Use specs_.guess(...) to populate a new starting point.
            specs_.guess(params_, paramIsFixed_, halton.nextSequence(),
                    iterations, forward_, t_, addParams_);
            // 3b) Run the optimizer (using a ProjectedCostFunction-style
            //     wrapper on the free dimensions; mirror C++ lines 200-217).
            //     ... existing optMethod_.minimize(...) call ...
            final double tmpInterpolationError = useMaxError_
                    ? interpolationMaxError() : interpolationError();
            if (tmpInterpolationError < bestError) {
                bestError = tmpInterpolationError;
                bestParams = params_.clone();
            }
        } while (++iterations < maxGuesses_ && bestError > errorAccept_);

        for (int i = 0; i < params_.length; ++i) params_[i] = bestParams[i];
        error_ = interpolationError();
        maxError_ = interpolationMaxError();
    }

    public double interpolationError() {
        double sum = 0.0;
        for (int i = 0; i < xBegin_.length; ++i) {
            final double diff = specs_.volatility(xBegin_[i], forward_, params_) - yBegin_[i];
            sum += weights_[i] * diff * diff;
        }
        return Math.sqrt(sum);
    }

    public double interpolationMaxError() {
        double maxAbs = 0.0;
        for (int i = 0; i < xBegin_.length; ++i) {
            final double diff = Math.abs(
                    specs_.volatility(xBegin_[i], forward_, params_) - yBegin_[i]);
            if (diff > maxAbs) maxAbs = diff;
        }
        return maxAbs;
    }
}
```

(The comment "existing optMethod_.minimize(...) call" marks where the implementer must wire up Java's existing `OptimizationMethod` API — `Problem`, `Constraint`, `CostFunction` shape — to call into the optimizer. Match patterns used elsewhere in `math.optimization`. If a non-mechanical type-system mismatch appears here — e.g. C++ uses `ProjectedCostFunction` and Java doesn't have an equivalent — A10 fires.)

- [ ] **Step 2: Compile.**

```bash
mvn -pl jquantlib test-compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Write `XABRInterpolation.java` outer wrapper.**

```java
package org.jquantlib.math.interpolations;

import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.OptimizationMethod;

/**
 * Outer wrapper for XABR-family interpolations. Mirrors the
 * `XABRInterpolation` class in C++ xabrinterpolation.hpp lines 263-310.
 */
public class XABRInterpolation<S extends XABRSpecs> {
    protected final XABRInterpolationImpl<S> impl_;

    public XABRInterpolation(
            final double[] x, final double[] y, final double t, final double forward,
            final double[] params, final boolean[] paramIsFixed,
            final boolean vegaWeighted, final EndCriteria endCriteria,
            final OptimizationMethod optMethod, final double errorAccept,
            final boolean useMaxError, final int maxGuesses,
            final double[] addParams, final S specs) {
        this.impl_ = new XABRInterpolationImpl<S>(x, y, t, forward, params,
                paramIsFixed, vegaWeighted, endCriteria, optMethod,
                errorAccept, useMaxError, maxGuesses, addParams, specs);
    }

    public XABRInterpolationImpl<S> impl() { return impl_; }
}
```

- [ ] **Step 4: Compile.**

```bash
mvn -pl jquantlib test-compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Do NOT commit yet — XABR scaffold + test land together via the SABR refactor.**

### Task C.6: Refactor `SABRInterpolation` to extend `XABRInterpolationImpl<SABRSpecs>` + fix inverted null-checks

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/math/interpolations/SABRInterpolation.java`

**Reference:** C++ `sabrinterpolation.hpp` lines 30-100 (SABRSpecs) and 102-200 (SABRInterpolationImpl extending XABRInterpolationImpl). The Java code currently has SABR's full impl flat inline; we want to lift the model-specific bits (param defaults, parameter constraints, transformation, volatility formula, per-restart guess) into a `SABRSpecs` class implementing `XABRSpecs`, and have the inner SABR impl extend `XABRInterpolationImpl<SABRSpecs>`.

- [ ] **Step 1: Add the `SABRSpecs` inner class.**

Locate the existing `SABRInterpolation.java`. Add a static nested class `SABRSpecs implements XABRSpecs`:

```java
    public static class SABRSpecs implements XABRSpecs {
        @Override public int dimension() { return 4; }

        @Override
        public void defaultValues(final double[] params, final boolean[] paramIsFixed,
                final double forward, final double t, final double[] addParams) {
            // Phase 2c WI-2 corrected formula: 0.2 factor OUTSIDE the ternary.
            if (Double.isNaN(params[0])) params[0] = 0.2 * ((Double.isNaN(params[1]) || params[1] < 0.9999)
                    ? Math.pow(forward, 1.0 - (Double.isNaN(params[1]) ? 0.5 : params[1]))
                    : 1.0);
            if (Double.isNaN(params[1])) params[1] = 0.5;
            if (Double.isNaN(params[2])) params[2] = Math.sqrt(0.4);
            if (Double.isNaN(params[3])) params[3] = 0.0;
        }

        @Override
        public void guess(final double[] values, final boolean[] paramIsFixed,
                final HaltonRsg.Sample sample, final int iteration,
                final double forward, final double t, final double[] addParams) {
            int idx = 0;
            // Mirror C++ sabrinterpolation.hpp lines 50-90: per-iter Halton-driven
            // guess synthesis for each free parameter, with iteration==0 retaining
            // the caller-supplied initial guess.
            if (!paramIsFixed[0]) values[0] = (iteration == 0 ? values[0]
                    : sample.value[idx++] * 1.0);
            if (!paramIsFixed[1]) values[1] = (iteration == 0 ? values[1]
                    : sample.value[idx++] * 1.0);
            if (!paramIsFixed[2]) values[2] = (iteration == 0 ? values[2]
                    : sample.value[idx++] * 1.0);
            if (!paramIsFixed[3]) values[3] = (iteration == 0 ? values[3]
                    : 2.0 * sample.value[idx++] - 1.0);  // ρ ∈ (-1, 1)
        }

        @Override
        public Array inverse(final Array y, final boolean[] paramIsFixed,
                final double[] params, final double forward) {
            // Reuse the existing SabrParametersTransformation inverse path.
            return new SabrParametersTransformation().inverse(y);
        }

        @Override
        public Array direct(final Array x, final boolean[] paramIsFixed,
                final double[] params, final double forward) {
            return new SabrParametersTransformation().direct(x);
        }

        @Override
        public double volatility(final double strike, final double forward,
                final double[] params) {
            return SabrFormula.sabrVolatility(strike, forward, /* expiryTime */ 1.0,
                    params[0], params[1], params[2], params[3]);
        }

        @Override
        public Constraint constraint(final double forward) {
            return new NoConstraint(); // SABR uses transformation, not box constraint
        }

        @Override
        public double weight(final double strike, final double forward,
                final double stdDev, final double[] addParams) {
            return BlackFormula.blackFormulaStdDevDerivative(strike, forward, stdDev);
        }
    }
```

(The `expiryTime=1.0` in `volatility(...)` is wrong — should plumb through `t_`. Either change the `XABRSpecs.volatility(...)` signature to include `t`, or capture `t` inside SABRSpecs. The latter requires SABRSpecs to be a non-static inner class with access to enclosing `t_`. Recommended: change the interface to include `t` parameter — cleaner.)

- [ ] **Step 2: Refactor inner `SABRInterpolationImpl` to extend `XABRInterpolationImpl<SABRSpecs>`.** Drop the duplicated fields (`endCriteria_`, `optMethod_`, `vegaWeighted_`, `weights_`, `forward_`, `itsCoeffs.t_`) — they live in the parent now. The constructor delegates to `super(...)`. The `update()` method becomes a thin wrapper around `calculate()` from the parent. Per-strike `value(x)` calls `specs_.volatility(x, forward_, params_)`.

- [ ] **Step 3: Fix inverted null-checks at lines 248-255.** The current code reads:

```java
if (optMethod_ != null) {
    optMethod_ = new Simplex(0.01);
}
if (endCriteria_ != null) {
    endCriteria_ = new EndCriteria(60000, 100, 1e-8, 1e-8, 1e-8);
}
```

The bug: it overwrites the caller-supplied value with the default. C++ semantic is the opposite — assign default only when null. After the refactor in Step 2, this entire block is gone (it lives in `XABRInterpolationImpl`'s constructor with the correct semantic baked in at Step C.5/Step 1). Verify the buggy code is no longer reachable.

- [ ] **Step 4: Compile.**

```bash
mvn -pl jquantlib test-compile -q
```

Expected: BUILD SUCCESS. If `SabrParametersTransformation`, `SabrFormula`, `BlackFormula.blackFormulaStdDevDerivative`, `NoConstraint` don't exist in Java with the exact names assumed, locate the equivalent and adjust imports.

- [ ] **Step 5: Run baseline (existing SABR tests should still pass).**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 641, Failures: 0, Errors: 0, Skipped: 24` (HaltonRsg test plus baseline; other SABR tests unchanged at this point — still `@Ignore`'d).

If any previously-passing SABR test fails, root-cause: most likely the `SABRSpecs.defaultValues` or `volatility(strike)` call doesn't match what the inner impl was doing before. Compare against `SABRCoeffHolder` setup.

- [ ] **Step 6: Do NOT commit yet — Tasks C.7 + C.8 land together.**

### Task C.7: Probes — XABR restart loop + SABR calibration

**Files:**
- Create: `migration-harness/cpp/probes/xabr_restart_loop_probe.cpp`
- Create: `migration-harness/cpp/probes/sabr_calibration_probe.cpp`

- [ ] **Step 1: Write `xabr_restart_loop_probe.cpp`.** Two cases:
  1. Single-iteration deterministic case: maxGuesses=1, paramIsFixed all false, well-posed initial guess. Capture (final params, error, maxError, end criteria).
  2. Multi-iteration convergence case: maxGuesses=10, errorAccept=1e-6. Capture same.

- [ ] **Step 2: Write `sabr_calibration_probe.cpp`.** Two fixtures matching the 2 currently-`@Ignore`'d Java tests:
  - Fixture #1: from `SABRInterpolationTest::testSabrInterpolation` — the original strikes / vols / forward / expiry.
  - Fixture #2: from `InterpolationTest::testSabrInterpolation` — same.

  For each fixture, instantiate C++ `SABRInterpolation` with the same construction parameters; call `update()`; capture (calibrated α, β, ν, ρ, error, maxError, end criteria, fitted vols at all strikes).

- [ ] **Step 3: Generate references.**

```bash
./migration-harness/scripts/generate-references.sh xabr_restart_loop_probe sabr_calibration_probe 2>&1 | tail -20
```

- [ ] **Step 4: Commit.**

```bash
git add migration-harness/cpp/probes/xabr_restart_loop_probe.cpp \
        migration-harness/cpp/probes/sabr_calibration_probe.cpp \
        migration-harness/cpp/probes/CMakeLists.txt \
        migration-harness/data/xabr_restart_loop_probe.json \
        migration-harness/data/sabr_calibration_probe.json
git commit -s -m "infra(harness): xabr_restart_loop + sabr_calibration probes (Phase 2d WI-3)"
```

### Task C.8: XABR + SABR tests; un-skip the 2 calibration tests

**Files:**
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/XABRInterpolationImplTest.java`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationCalibrationTest.java`
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java` (remove `@Ignore` on the calibration test)
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/InterpolationTest.java` (remove `@Ignore` on `testSabrInterpolation`)

- [ ] **Step 1: Write `XABRInterpolationImplTest.java`.** Two methods, one per probe case. Cross-validate `(error, maxError, params)` after calling `calculate()`.

```java
package org.jquantlib.testsuite.math.interpolations;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.interpolations.SABRInterpolation;
import org.jquantlib.math.interpolations.XABRInterpolationImpl;
import org.jquantlib.testsuite.harness.ReferenceReader;
import org.junit.Test;

public class XABRInterpolationImplTest {

    @Test
    public void testSingleIterationDeterministic() {
        final var ref = ReferenceReader.load("xabr_restart_loop_probe.json")
                .getObject("single_iteration");
        // ... build XABRInterpolationImpl<SABRSpecs> with the same fixture as the probe
        // ... assertEquals on (error, maxError, params) at tight tier
    }

    @Test
    public void testMultiIterationConvergence() {
        final var ref = ReferenceReader.load("xabr_restart_loop_probe.json")
                .getObject("multi_iteration");
        // ... same shape, multi-iter case
    }
}
```

- [ ] **Step 2: Write `SABRInterpolationCalibrationTest.java`.** Cross-validate the full calibration path on a fresh fixture (different from the 2 un-skipped tests below — keeps test independence).

- [ ] **Step 3: Un-skip `SABRInterpolationTest::testSabrInterpolation`.** Find the `@Ignore` annotation on the test method (added in Phase 2c WI-2's `1680bbf`); delete it. Replace any hardcoded reference values with `ReferenceReader.load("sabr_calibration_probe.json").getObject("fixture_1")`. Tight tier on calibrated params; loose tier on fitted vols (LM + Halton restart noise floor).

- [ ] **Step 4: Un-skip `InterpolationTest::testSabrInterpolation`.** Same as Step 3 but for the `InterpolationTest` file with `getObject("fixture_2")`.

- [ ] **Step 5: Run.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 644, Failures: 0, Errors: 0, Skipped: 22` (+3 new tests: XABR×2 + SABR calibration; +2 from un-skipping; net `Skipped` 24 → 22). If A and B have already merged, the absolute count will be higher accordingly (e.g. `646`).

If any of the 2 un-skipped tests fail at tight on params: **do NOT loosen.** Root-cause first. Likely culprits:
1. `SABRSpecs.guess(...)` doesn't faithfully port the C++ per-iter formula.
2. `XABRInterpolationImpl.calculate()` Halton restart-loop termination differs (e.g. iterations counter off-by-one).
3. The fixture in the probe doesn't match the Java test fixture — pin both to identical inputs.

If it fails at loose on fitted vols beyond `1e-8`, document the per-test exception with inline justification (LM convergence noise — expected per design §4.4).

- [ ] **Step 6: Run scanner.**

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected: `work_in_progress: 1` (only G2; CapHelper closed by worktree A; no new WIPs introduced by C).

- [ ] **Step 7: Commit (XABR scaffold + SABR refactor + tests + un-skips).**

```bash
git add jquantlib/src/main/java/org/jquantlib/math/interpolations/XABRSpecs.java \
        jquantlib/src/main/java/org/jquantlib/math/interpolations/XABRCoeffHolder.java \
        jquantlib/src/main/java/org/jquantlib/math/interpolations/XABRInterpolationImpl.java \
        jquantlib/src/main/java/org/jquantlib/math/interpolations/XABRInterpolation.java \
        jquantlib/src/main/java/org/jquantlib/math/interpolations/SABRInterpolation.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/XABRInterpolationImplTest.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationCalibrationTest.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/InterpolationTest.java
git commit -s -m "stub(math.interpolations): port XABR scaffold + refactor SABR to XABRInterpolationImpl<SABRSpecs> + un-skip 2 calibration tests (Phase 2d WI-3)"
```

### Task C.9: Land worktree C to main

- [ ] **Step 1: Push the branch.**

```bash
git push origin phase-2d-C-sabr-xabr
```

- [ ] **Step 2: From the MAIN checkout, fast-forward.**

```bash
git -C /Users/josemoya/eclipse-workspace/jquantlib fetch origin
git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/phase-2d-C-sabr-xabr
git -C /Users/josemoya/eclipse-workspace/jquantlib log --oneline -8
git -C /Users/josemoya/eclipse-workspace/jquantlib push origin main
```

If `merge --ff-only` refuses because A and/or B landed first, rebase C's worktree onto new main:

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2d-C
git fetch origin
git rebase origin/main
git push --force-with-lease origin phase-2d-C-sabr-xabr
```

Then re-attempt the merge from main checkout. Conflicts → A9 fires.

---

## Layer 2 — Completion doc + tag

### Task L2.1: Write `phase2d-completion.md`

**Files:**
- Create: `docs/migration/phase2d-completion.md`

- [ ] **Step 1: From the main checkout, gather final state.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
python3 tools/stub-scanner/scan_stubs.py
git log --oneline 4cbabec..HEAD
```

Expected: `Tests run: ~648, Failures: 0, Errors: 0, Skipped: 22`; scanner `work_in_progress: 1` (G2 only); 9-12 commits since Phase 2c tip.

- [ ] **Step 2: Write the completion doc** following Phase 2c's structure:
  - Header (date, predecessor tag, what's in)
  - Per-WI summary with commit hashes
  - Final scanner state
  - Test suite final state with delta table
  - Deviations from plan (any per-test loose-tier exceptions, any fixture-fix scope creep, any A4/A8/A9/A10 firings)
  - Phase 2e seed list (G2/TreeLattice2D + carved BroadieKaya×3 + carved SwaptionHelper + any carry-forward items surfaced)
  - Worktree cleanup checklist

- [ ] **Step 3: Commit.**

```bash
git add docs/migration/phase2d-completion.md
git commit -s -m "docs(migration): Phase 2d completion report"
git push origin main
```

### Task L2.2: Tag `jquantlib-phase2d-complete` and push

- [ ] **Step 1:** Tag and push.

```bash
git tag jquantlib-phase2d-complete
git push origin jquantlib-phase2d-complete
git tag -l 'jquantlib-phase2*'
```

Expected: 4 tags now (`phase1`, `phase2a`, `phase2b`, `phase2c`, `phase2d` — depending on how many are present locally).

### Task L2.3: Worktree cleanup

- [ ] **Step 1: Remove the 3 worktrees.**

```bash
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2d-A 2>&1
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2d-B 2>&1
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2d-C 2>&1
git worktree prune
git worktree list
```

If any `remove --force` fails with "Directory not empty" (Phase 2c precedent — likely submodule artifacts), fall back to:

```bash
rm -rf /Users/josemoya/eclipse-workspace/jquantlib-2d-A
rm -rf /Users/josemoya/eclipse-workspace/jquantlib-2d-B
rm -rf /Users/josemoya/eclipse-workspace/jquantlib-2d-C
git worktree prune
```

- [ ] **Step 2: Delete the branches local + remote.**

```bash
git branch -D phase-2d-A-caphelper phase-2d-B-nccv phase-2d-C-sabr-xabr 2>&1 || true
git push origin --delete phase-2d-A-caphelper phase-2d-B-nccv phase-2d-C-sabr-xabr 2>&1
```

- [ ] **Step 3: Update memory.**

Update `/Users/josemoya/.claude/projects/-Users-josemoya-eclipse-workspace-jquantlib/memory/project_jquantlib_migration.md` description and body with Phase 2d milestone (date, tag, tip, test count delta, scanner WIP delta, key deviations).

- [ ] **Step 4: Final verification.**

```bash
git status
git log --oneline -10
git tag -l 'jquantlib-phase2*'
git worktree list
git branch -a | grep '2d' || echo "no 2d branches"
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
python3 tools/stub-scanner/scan_stubs.py
```

Expected:
- branch `main`, working tree clean
- log shows recent Phase 2d commits + the completion doc
- `phase2d-complete` tag exists
- only the main worktree exists
- no `2d` branches anywhere
- tests `Failures: 0, Errors: 0, Skipped: 22`
- scanner `work_in_progress: 1` (G2)

---

## Self-Review notes

- All 9 design exit criteria mapped to tasks: §7.1 (mvn green) → final verification L2.3 Step 4; §7.2 (test delta) → A.6 + B.4 + C.3 + C.8; §7.3 (Skipped: 22) → C.8 Step 5; §7.4 (scanner WIP=1) → A.4 Step 4 + C.8 Step 6; §7.5 (worktrees gone) → L2.3; §7.6 (probes regenerate) → A.5 + B.3 + C.2 + C.7 use `generate-references.sh`; §7.7 (loose-tier inline justification) → enforced per task (A.6, B.4, C.8); §7.8 (completion doc) → L2.1; §7.9 (tag pushed + memory updated) → L2.2 + L2.3.
- All 5 design pause triggers covered: A4 → B.2 Step 4 (NCCV closed-form, no quadrature needed); A6 disabled (no end-of-layer pauses required); A9 → A.7/B.5/C.9 step 2 (rebase-conflict path); A10 → C.5 Step 1 (XABR template-to-generics gotcha pause point); A1/A2/A3/A7 inherit silently from prior phases.
- The plan does not invent classes/methods that don't exist. Where Java-side API shape isn't pinned by the existing code base I've read, the plan calls out "verify against actual" with the search command — concrete and actionable.
- All commit messages follow the `<kind>(<pkg>): <verb>` convention with the `(Phase 2d WI-N)` suffix used in Phase 2c.
- The plan keeps SwaptionHelper untouched per P2D-3 (only the compile-only base-class change, no body port, no test).
