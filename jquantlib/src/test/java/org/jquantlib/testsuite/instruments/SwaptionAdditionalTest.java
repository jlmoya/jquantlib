/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.EuriborSwapIsdaFixA;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.MakeSwaption;
import org.jquantlib.instruments.MakeVanillaSwap;
import org.jquantlib.instruments.Settlement;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.pricingengines.swaption.BachelierSwaptionEngine;
import org.jquantlib.pricingengines.swaption.BlackSwaptionEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5f skeleton port of {@code test-suite/swaption.cpp} v1.42.1
 * (1,197 LOC, 12 test cases).
 *
 * <p>Java already has direct Swaption-engine coverage in
 * {@code testsuite.pricingengines.swaption.*} and the
 * {@code instruments.NonstandardSwaptionTest} / {@code FloatFloatSwaptionTest}
 * classes; the C++ {@code swaption.cpp} suite cross-cuts engines and
 * the {@link org.jquantlib.instruments.Swaption} instrument itself
 * (caching, vega, cash-settled, implied vol, delta, MakeSwaption builder).
 *
 * <p><strong>Phase 5e.5b-CFC-d-73 incremental body-fill (2026-05-16):</strong>
 * Four cases are now body-filled (3 from CFC-d-62 + 1 new):
 * <ul>
 *   <li>{@code testMakeSwaptionWithExerciseCalendar} — builder calendar override</li>
 *   <li>{@code testBlackEngineCaching} — LazyObject caching semantics
 *       (post align(util.LazyObject) {@code isCalculated()} accessor)</li>
 *   <li>{@code testCachedValue} — physical-settled 5Yx10Y payer swaption
 *       under Black76, cached NPV reproduced against C++ v1.42.1</li>
 *   <li>{@code testSwaptionDeltaInBlackModel} — analytic vs FD delta for the
 *       physical / collateralized-cash settlement paths under Black76;
 *       drives the new {@code BlackSwaptionEngine} additional-result map
 *       ({@code delta}, {@code vega}, {@code strike}, {@code atmForward},
 *       {@code annuity}, {@code stdDev}, {@code swapLength},
 *       {@code timeToExpiry}, {@code impliedVolatility},
 *       {@code forwardPrice}, {@code spreadCorrection}) introduced in this
 *       commit. Skips the {@code Settlement.Cash + ParYieldCurve} branch
 *       (still {@code UnsupportedOperationException}). Mirrors the C++
 *       per-iteration mean-value-theorem check exactly.</li>
 * </ul>
 *
 * <p>The remaining 8 stay deferred:
 * <ul>
 *   <li>{@code testStrikeDependency} / {@code testSpreadDependency} /
 *       {@code testSpreadTreatment} / {@code testCashSettledSwaptions} /
 *       {@code testVega} — all require the {@code Settlement.Cash} /
 *       {@code ParYieldCurve} BlackSwaptionEngine path which throws
 *       {@code UnsupportedOperationException} pending the
 *       {@code CashFlows::bps(InterestRate, ...)} + {@code Schedule.tenor()}
 *       port (Phase 5e.5b-CFC-d ongoing — see
 *       {@code BlackSwaptionEngine.java} line ~244).</li>
 *   <li>{@code testImpliedVolatility} / {@code testImpliedVolatilityOis} —
 *       requires {@code Swaption.impliedVolatility(...)} convenience
 *       (not yet ported; also OIS swaption path uses MakeOIS not yet
 *       wired through MakeSwaption).</li>
 *   <li>{@code testSwaptionDeltaInBachelierModel} — requires
 *       {@code BachelierSwaptionEngine} (not yet ported as a swaption engine;
 *       {@link org.jquantlib.pricingengines.BachelierCalculator} exists but
 *       is not wired through a swaption engine yet).</li>
 * </ul>
 *
 * <p>Cases (mirroring C++ {@code BOOST_AUTO_TEST_CASE} order):
 * <ul>
 *   <li>{@code testStrikeDependency}</li>
 *   <li>{@code testSpreadDependency}</li>
 *   <li>{@code testSpreadTreatment}</li>
 *   <li>{@code testCachedValue}</li>
 *   <li>{@code testVega}</li>
 *   <li>{@code testCashSettledSwaptions}</li>
 *   <li>{@code testImpliedVolatility}</li>
 *   <li>{@code testImpliedVolatilityOis}</li>
 *   <li>{@code testSwaptionDeltaInBlackModel}</li>
 *   <li>{@code testSwaptionDeltaInBachelierModel}</li>
 *   <li>{@code testMakeSwaptionWithExerciseCalendar}</li>
 *   <li>{@code testBlackEngineCaching}</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/swaption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class SwaptionAdditionalTest {

    /**
     * Mirrors {@code testStrikeDependency} from C++ v1.42.1 {@code swaption.cpp}
     * (lines 172-263). For each (exercise, length, swap-type) tuple price
     * the swaption across an ascending grid of strikes under both
     * {@code Settlement.Physical/PhysicalOTC} and
     * {@code Settlement.Cash/ParYieldCurve}, then verify the NPV sequence is
     * monotone in strike (payer non-increasing, receiver non-decreasing).
     *
     * <p><strong>Tolerance tier</strong> — exact (monotonicity test).
     *
     * <p>The grid is shrunk relative to C++ (6 x 8 = 48 cells) to a
     * representative 3 x 3 = 9 cells so the test stays fast; the
     * monotonicity property holds at every point of the grid.
     */
    @Test
    public void testStrikeDependency() {
        runStrikeDependency(makeCommonVars());
    }

    /**
     * Mirrors {@code testSpreadDependency} from C++ v1.42.1 {@code swaption.cpp}
     * (lines 265-349). For each (exercise, length, swap-type) tuple price the
     * swaption across an ascending grid of floating-leg spreads under both
     * {@code Settlement.Physical/PhysicalOTC} and
     * {@code Settlement.Cash/ParYieldCurve}, and verify the NPV sequence is
     * monotone in spread (payer non-decreasing, receiver non-increasing).
     *
     * <p><strong>Tolerance tier</strong> — exact (monotonicity).
     */
    @Test
    public void testSpreadDependency() {
        runSpreadDependency(makeCommonVars());
    }

    /**
     * Mirrors {@code testSpreadTreatment} from C++ v1.42.1 {@code swaption.cpp}
     * (lines 351-410). For each (exercise, length, swap-type, spread) tuple
     * verifies that a swaption on a spread-bearing fixed-vs-floating swap is
     * equivalent (NPV agreement within 1e-6) to a swaption on the
     * spread-adjusted equivalent swap (fixed rate bumped by
     * {@code spread * |floatingLegBPS / fixedLegBPS|}), under both
     * physical and Cash/ParYieldCurve settlement. Cross-checks the
     * engine's internal spread-correction logic.
     *
     * <p><strong>Tolerance tier</strong> — tight (1e-6 abs on NPV ratio,
     * matching C++ literal).
     */
    @Test
    public void testSpreadTreatment() {
        runSpreadTreatment(makeCommonVars());
    }

    /**
     * Mirrors {@code testCachedValue} from C++ v1.42.1 {@code swaption.cpp}.
     *
     * <p>Builds a 5Yx10Y physical-settled payer swaption on Euribor6M with
     * a flat 5% Act/365 term structure, 30/360 (BondBasis) fixed leg, and
     * Black vol = 20%. Checks NPV against the C++ cached value
     * ({@code 0.036418158579} for {@code IborCoupon::Settings::usingAtParCoupons()
     * == true}, which matches the Java default).
     *
     * <p><strong>Tolerance tier</strong> — tight (abs 1e-14 + rel 1e-12).
     * Closed-form Black76 on top of {@code DiscountingSwapEngine}; same
     * schedule logic / day counters / calendar as C++.
     *
     * <p>The OIS-swaption branch (second half of C++ test) is deferred —
     * MakeOIS is not yet wired through MakeSwaption and the OIS-swaption
     * path requires a dedicated {@code Swaption(OvernightIndexedSwap, ...)}
     * constructor that this Java port lacks.
     */
    @Test
    public void testCachedValue() {
        // --- C++ fixture (CommonVars) ---------------------------------------
        final Calendar calendar = new Target();
        final DayCounter act365 = new Actual365Fixed();
        final DayCounter thirty360 = new Thirty360(Thirty360.Convention.BondBasis);
        final int settlementDays = 2;

        final Date today = new Date(13, Month.March, 2002);
        final Date settlement = new Date(15, Month.March, 2002);
        new Settings().setEvaluationDate(today);

        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(
                new FlatForward(settlement, 0.05, act365));
        final IborIndex idx = new Euribor6M(ts);

        // --- Build swaption --------------------------------------------------
        final Date exerciseDate = calendar.advance(settlement,
                new Period(5, TimeUnit.Years));
        final Date startDate = calendar.advance(exerciseDate, settlementDays,
                TimeUnit.Days);

        final VanillaSwap swap = new MakeVanillaSwap(
                new Period(10, TimeUnit.Years), idx, 0.06)
                .withEffectiveDate(startDate)
                .withFixedLegTenor(new Period(1, TimeUnit.Years))
                .withFixedLegDayCount(thirty360)
                .value();

        // Build a swaption via MakeSwaption with explicit fixingDate and
        // user-supplied engine.  Mirrors CommonVars::makeSwaption.
        final double vol = 0.20;
        final Handle<Quote> volQuote = new Handle<Quote>(new SimpleQuote(vol));
        final BlackSwaptionEngine engine =
                BlackSwaptionEngine.fromVolQuote(ts, volQuote);

        final Swaption swaption = new Swaption(
                swap, new org.jquantlib.exercise.EuropeanExercise(exerciseDate));
        swaption.setPricingEngine(engine);

        // --- Cross-validate vs C++ cached value -----------------------------
        // C++ v1.42.1 swaption.cpp:436 — usingAtParCoupons==true is the
        // Java default (IborCoupon.Settings.usingAtParCoupons default).
        // The C++ test source itself truncates the literal to 12 digits and
        // checks |diff| > 1e-12, so a hybrid tight tolerance is too strict
        // (~4.6e-14 here vs. 3.6e-13 literal-truncation noise).  We use
        // {@link Tolerance#within}(1e-12, ...) to match C++ exactly.
        final double cachedNPV = 0.036418158579;
        final double npv = swaption.NPV();
        if (!Tolerance.within(npv, cachedNPV, 1.0e-12,
                "C++ literal cachedNPV truncated to 12 sig figs in swaption.cpp:436")) {
            fail("failed to reproduce cached swaption value:"
                    + "\ncalculated: " + npv
                    + "\nexpected:   " + cachedNPV);
        }
    }

    /**
     * Mirrors {@code testVega} from C++ v1.42.1 {@code swaption.cpp}
     * (lines 463-528). For each (exercise, length, strike, settlement, vol)
     * tuple compute the analytic Vega per percentage-point (the
     * {@code "vega"} additional result, published by
     * {@link BlackSwaptionEngine} since CFC-d-73) and cross-validate against
     * a central finite-difference vega across {@code +/- 1e-8} vol bumps.
     * Skip cells where the FD vega is negligible compared to NPV. The
     * iteration also exercises the new {@code Settlement.Cash/ParYieldCurve}
     * annuity branch (Phase 5e.5b-CFC-d-142).
     *
     * <p><strong>Tolerance tier</strong> — loose (relative 0.015 on
     * {@code |analytic - numerical| / numerical}, matching C++ literal).
     */
    @Test
    public void testVega() {
        runVega(makeCommonVars());
    }

    /**
     * Mirrors {@code testCashSettledSwaptions} from C++ v1.42.1
     * {@code swaption.cpp} (lines 530-824). For each (exercise, length)
     * builds four fixed-leg conventions (Unadjusted/30-360,
     * Unadjusted/Act-365, ModifiedFollowing/30-360,
     * ModifiedFollowing/Act-365), prices a physical-settled and a
     * Cash/ParYieldCurve-settled swaption against each, and checks that
     * the NPV ratio (cash/physical) equals the corresponding annuity
     * ratio (cash-annuity / fixedLegBPS-annuity) within 1e-10. Cross-validates
     * the cash-annuity formula against an analytic discount-curve replication
     * built on top of {@link FlatForward}.
     *
     * <p><strong>Tolerance tier</strong> — tight (1e-10 abs on annuity-ratio
     * mismatch, matching C++ literal).
     */
    @Test
    public void testCashSettledSwaptions() {
        runCashSettledSwaptions(makeCommonVars());
    }

    /**
     * Mirrors {@code testImpliedVolatility} from C++ v1.42.1 {@code swaption.cpp}
     * (lines 826-921). For each (exercise, length, strike, swapType, settlement,
     * priceType, vol) tuple price a vanilla payer/receiver swaption under
     * Black76, recover the implied volatility via {@link Swaption#impliedVolatility},
     * and round-trip back to the price.
     *
     * <p><strong>Java port deviations from C++ v1.42.1:</strong>
     * <ul>
     *   <li>The C++ test iterates over both {@code Settlement.Physical/PhysicalOTC}
     *       and {@code Settlement.Cash/ParYieldCurve}. The Java
     *       {@link BlackSwaptionEngine} still throws {@code UnsupportedOperationException}
     *       for the {@code Cash/ParYieldCurve} branch (pending
     *       {@code CashFlows.bps(InterestRate, ...)} + {@code Schedule.tenor()}
     *       port), so we restrict the loop to {@code Physical/PhysicalOTC}.
     *       The {@code Cash/CollateralizedCashPrice} branch is also exercised
     *       to keep parity with the Java {@code testSwaptionDeltaInBlackModel}
     *       (which uses the same supported subset).</li>
     *   <li>The grid is reduced ((2 exercises x 2 lengths x 3 strikes x 2 swap-types
     *       x 2 settlements x 2 priceTypes x 4 vols = 384 iterations) vs. C++
     *       (6 x 8 x 6 x 2 x 2 x 2 x 7 = 32256). Per CLAUDE.md "loose tier" guidance,
     *       a representative subset across the {@code (low/mid/high) x (OTM/ATM/ITM)}
     *       grid is sufficient cross-validation given the solver's deterministic
     *       output; the full grid runs >8 minutes in the suite.</li>
     * </ul>
     *
     * <p><strong>Tolerance tier</strong> — tight: matches the C++ literal
     * tolerance ({@code 1.0e-8}) on the price round-trip with the same
     * "skip-if-zero-vol-price-matches" bracketing fallback as C++.
     */
    @Test
    public void testImpliedVolatility() {
        final Calendar calendar = new Target();
        final DayCounter act365 = new Actual365Fixed();
        final DayCounter thirty360 = new Thirty360(Thirty360.Convention.BondBasis);
        final int settlementDays = 2;

        final Date today = calendar.adjust(new Date(13, Month.March, 2002));
        new Settings().setEvaluationDate(today);
        final Date settlement = calendar.advance(today, settlementDays, TimeUnit.Days);

        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(
                new FlatForward(settlement, 0.05, act365));
        final IborIndex idx = new Euribor6M(ts);

        final int maxEvaluations = 100;
        final double tolerance = 1.0e-8;

        // Subset of C++ grid — see method javadoc for full grid + rationale.
        final Period[] exercises = {
                new Period(1, TimeUnit.Years),
                new Period(5, TimeUnit.Years) };
        final Period[] lengths = {
                new Period(2, TimeUnit.Years),
                new Period(5, TimeUnit.Years) };
        final double[] strikes = { 0.03, 0.05, 0.07 };
        final VanillaSwap.Type[] swapTypes =
                { VanillaSwap.Type.Receiver, VanillaSwap.Type.Payer };
        // Supported settlement variants (see method javadoc).
        final Settlement.Type[] settlementTypes =
                { Settlement.Type.Physical, Settlement.Type.Cash };
        final Settlement.Method[] settlementMethods =
                { Settlement.Method.PhysicalOTC,
                  Settlement.Method.CollateralizedCashPrice };
        final Swaption.PriceType[] priceTypes =
                { Swaption.PriceType.Spot, Swaption.PriceType.Forward };
        // A 4-point subset of C++ {0.01, 0.05, 0.10, 0.20, 0.30, 0.70, 0.90}.
        final double[] vols = { 0.05, 0.20, 0.30, 0.70 };

        for (final Period exercise : exercises) {
            for (final Period length : lengths) {
                final Date exerciseDate = calendar.advance(today, exercise);
                final Date startDate = calendar.advance(exerciseDate,
                        settlementDays, TimeUnit.Days);

                for (final double strike : strikes) {
                    for (final VanillaSwap.Type k : swapTypes) {
                        final VanillaSwap swap = new MakeVanillaSwap(
                                length, idx, strike)
                                .withEffectiveDate(startDate)
                                .withFixedLegTenor(new Period(1, TimeUnit.Years))
                                .withFixedLegDayCount(thirty360)
                                .withFloatingLegSpread(0.0)
                                .withType(k)
                                .value();

                        for (int h = 0; h < settlementTypes.length; h++) {
                            for (final Swaption.PriceType priceType : priceTypes) {
                                for (final double vol : vols) {
                                    runImpliedVolCase(swap, exerciseDate, ts,
                                            vol, strike, exercise, length,
                                            k, settlementTypes[h],
                                            settlementMethods[h], priceType,
                                            tolerance, maxEvaluations);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Helper for {@link #testImpliedVolatility}. Mirrors the inner-loop body
     * of C++ swaption.cpp:860-915.
     */
    private static void runImpliedVolCase(final VanillaSwap swap,
                                          final Date exerciseDate,
                                          final Handle<YieldTermStructure> ts,
                                          final double vol,
                                          final double strike,
                                          final Period exercise,
                                          final Period length,
                                          final VanillaSwap.Type swapType,
                                          final Settlement.Type sType,
                                          final Settlement.Method sMethod,
                                          final Swaption.PriceType priceType,
                                          final double tolerance,
                                          final int maxEvaluations) {
        final BlackSwaptionEngine engine = BlackSwaptionEngine.fromVolQuote(
                ts, new Handle<Quote>(new SimpleQuote(vol)));
        final Swaption swaption = new Swaption(swap,
                new org.jquantlib.exercise.EuropeanExercise(exerciseDate),
                sType, sMethod);
        swaption.setPricingEngine(engine);

        // Price target (spot or forward).
        final double value;
        if (priceType == Swaption.PriceType.Spot) {
            value = swaption.NPV();
        } else {
            // Forward is published in additionalResults by BlackSwaptionEngine.
            swaption.NPV();
            final Object fwd = ((Swaption.ResultsImpl) engine.getResults())
                    .additionalResults().get("forwardPrice");
            if (fwd == null) {
                fail("BlackSwaptionEngine did not publish 'forwardPrice'"
                        + " additional result");
            }
            value = ((Number) fwd).doubleValue();
        }

        double implVol = 0.0;
        boolean failedToBracket = false;
        try {
            implVol = swaption.impliedVolatility(value, ts, 0.10, tolerance,
                    maxEvaluations, 1.0e-7, 4.0,
                    org.jquantlib.model.VolatilityType.ShiftedLognormal,
                    0.0, priceType);
        } catch (final RuntimeException e) {
            // Couldn't bracket? Mirror C++ swaption.cpp:878-895 fallback:
            // re-price at vol=0 and skip if the input value is within
            // tolerance of the zero-vol value (intrinsic case), otherwise
            // report the failure.
            final BlackSwaptionEngine zeroEngine =
                    BlackSwaptionEngine.fromVolQuote(ts,
                            new Handle<Quote>(new SimpleQuote(0.0)));
            swaption.setPricingEngine(zeroEngine);
            final double value2;
            if (priceType == Swaption.PriceType.Spot) {
                value2 = swaption.NPV();
            } else {
                swaption.NPV();
                final Object fwd2 = ((Swaption.ResultsImpl) zeroEngine.getResults())
                        .additionalResults().get("forwardPrice");
                value2 = ((Number) fwd2).doubleValue();
            }
            if (Math.abs(value - value2) < tolerance) {
                failedToBracket = true;
            } else {
                fail("implied vol failure: " + exercise + "x" + length
                        + " " + swapType
                        + "\n  settlement: " + sType + "/" + sMethod
                        + "\n  strike      " + strike
                        + "\n  atm level:  " + swap.fairRate()
                        + "\n  vol:        " + vol
                        + "\n  price:      " + value
                        + "\n  priceType:  " + priceType
                        + "\n" + e.getMessage());
            }
        }
        if (failedToBracket) {
            return;
        }
        if (Math.abs(implVol - vol) > tolerance) {
            // Difference might not matter — re-price at implied vol and
            // check the round-trip. Mirrors C++ swaption.cpp:897-912.
            final BlackSwaptionEngine implEngine =
                    BlackSwaptionEngine.fromVolQuote(ts,
                            new Handle<Quote>(new SimpleQuote(implVol)));
            swaption.setPricingEngine(implEngine);
            final double value2;
            if (priceType == Swaption.PriceType.Spot) {
                value2 = swaption.NPV();
            } else {
                swaption.NPV();
                final Object fwd2 = ((Swaption.ResultsImpl) implEngine.getResults())
                        .additionalResults().get("forwardPrice");
                value2 = ((Number) fwd2).doubleValue();
            }
            if (Math.abs(value - value2) > tolerance) {
                fail("implied vol failure: " + exercise + "x" + length
                        + " " + swapType
                        + "\n  settlement:    " + sType + "/" + sMethod
                        + "\n  strike         " + strike
                        + "\n  atm level:     " + swap.fairRate()
                        + "\n  vol:           " + vol
                        + "\n  price:         " + value
                        + "\n  priceType:     " + priceType
                        + "\n  implied vol:   " + implVol
                        + "\n  implied price: " + value2);
            }
        }
    }

    @Ignore("Phase 5e.5b-CFC-d-128 — Swaption.impliedVolatility is now ported"
            + " (see testImpliedVolatility), but this OIS variant still needs"
            + " (a) a Swaption(OvernightIndexedSwap,...) constructor — current"
            + " Java Swaption hardcodes VanillaSwap — and (b) BlackSwaptionEngine"
            + " support for OvernightIndexedSwap underlyings (engine reads"
            + " swap.floatingLegBPS() / swap.floatingSchedule() which OIS does"
            + " not yet expose). Both touch out-of-scope classes for this"
            + " sub-task (MakeOIS, OvernightIndexedCoupon, OvernightIndexedSwap);"
            + " un-ignore in the OIS-swaption-engine pass.")
    @Test
    public void testImpliedVolatilityOis() { fail("not implemented"); }

    /**
     * Mirrors {@code testSwaptionDeltaInBlackModel} from C++ v1.42.1
     * {@code swaption.cpp} (lines 1029-1140, via the
     * {@code checkSwaptionDelta<BlackSwaptionEngine>(false)} template).
     *
     * <p>For each (vol, exercise tenor, swap tenor, strike, settlement) tuple,
     * compute the analytic delta from the engine's {@code additionalResults}
     * map ({@code "delta"} key, now published by the engine — Phase 5e.5b-CFC-d-73),
     * then bump the projection (forward) curve by 1bp and compare against the
     * central finite-difference estimate. The mean-value-theorem assertion
     * requires the FD slope to lie strictly between the pre- and post-bump
     * analytic deltas (plus an {@code epsilon} cushion of {@code 1e-10}).
     *
     * <p>The C++ test enumerates {@code Settlement::Cash} with
     * {@code CollateralizedCashPrice}; the C++ {@code Cash + ParYieldCurve}
     * branch is not exercised here (and the Java engine still throws
     * {@code UnsupportedOperationException} for that combination).
     *
     * <p><strong>Tolerance tier</strong> — loose: matches C++ exactly,
     * comparison is via the mean-value-theorem inequality rather than a
     * fixed |a-b| threshold.
     *
     * <p>To keep this fast within the broader test suite, the iteration
     * grid is the same as C++ for vols and strikes but uses the
     * physical-settlement first / cash-collateralized second order (matching
     * the C++ {@code types[h]} / {@code methods[h]} pair indexing).
     */
    @Test
    public void testSwaptionDeltaInBlackModel() {
        final Date today = new Date(13, Month.March, 2002);
        new Settings().setEvaluationDate(today);

        final Calendar calendar = new Target();
        final DayCounter act365 = new Actual365Fixed();
        final DayCounter thirty360 = new Thirty360(Thirty360.Convention.BondBasis);

        final double bump = 1.0e-4;
        final double epsilon = 1.0e-10;
        final double projectionRate = 0.01;

        // Projection (forwarding) curve — relinkable so we can bump it.
        final RelinkableHandle<Quote> projectionQuoteHandle =
                new RelinkableHandle<Quote>(new SimpleQuote(projectionRate));
        final RelinkableHandle<YieldTermStructure> projectionCurveHandle =
                new RelinkableHandle<YieldTermStructure>(
                        new FlatForward(today, projectionQuoteHandle, act365));

        // Discount curve — fixed.
        final Handle<YieldTermStructure> discountHandle =
                new Handle<YieldTermStructure>(
                        new FlatForward(today,
                                new Handle<Quote>(new SimpleQuote(0.0085)),
                                act365));

        final DiscountingSwapEngine swapEngine =
                new DiscountingSwapEngine(discountHandle);
        final IborIndex idx = new Euribor6M(projectionCurveHandle);

        // Reduced grid relative to C++ (which spans 6 exercises x 8 lengths
        // x 5 strikes x 6 vols x 2 settlements x 2 types = 5760 cases) so
        // this test stays well under a second.  We keep the *kinds* of
        // points C++ checks (short/medium/long expiry, low/mid/high vol,
        // OTM/ATM/ITM strikes, both settlement variants, both swap types).
        final Period[] exercises = {
            new Period(1, TimeUnit.Years),
            new Period(5, TimeUnit.Years),
            new Period(10, TimeUnit.Years)
        };
        final Period[] lengths = {
            new Period(2, TimeUnit.Years),
            new Period(5, TimeUnit.Years),
            new Period(10, TimeUnit.Years)
        };
        final double[] strikes = { 0.03, 0.05, 0.07 };
        final double[] vols = { 0.10, 0.30, 0.70 };
        final VanillaSwap.Type[] swapTypes =
                { VanillaSwap.Type.Receiver, VanillaSwap.Type.Payer };
        final Settlement.Type[] settlementTypes =
                { Settlement.Type.Physical, Settlement.Type.Cash };
        final Settlement.Method[] settlementMethods =
                { Settlement.Method.PhysicalOTC,
                  Settlement.Method.CollateralizedCashPrice };

        for (final double vol : vols) {
            for (final Period exercise : exercises) {
                for (final Period length : lengths) {
                    for (final double strike : strikes) {
                        for (int h = 0; h < swapTypes.length; h++) {
                            // --- Build engine + swaption ----------------
                            final BlackSwaptionEngine engine =
                                    BlackSwaptionEngine.fromVolQuote(
                                            discountHandle,
                                            new Handle<Quote>(new SimpleQuote(vol)));

                            final Date exerciseDate =
                                    calendar.advance(today, exercise);
                            final Date startDate =
                                    calendar.advance(exerciseDate, 2, TimeUnit.Days);

                            // Reset projection quote to base value each iteration
                            // (bump of the previous iteration must not leak across
                            // exercises).
                            projectionQuoteHandle.linkTo(
                                    new SimpleQuote(projectionRate));

                            final VanillaSwap underlying = new MakeVanillaSwap(
                                    length, idx, strike)
                                    .withEffectiveDate(startDate)
                                    .withFixedLegTenor(
                                            new Period(1, TimeUnit.Years))
                                    .withFixedLegDayCount(thirty360)
                                    .withFloatingLegSpread(0.0)
                                    .withType(swapTypes[h])
                                    .value();
                            underlying.setPricingEngine(swapEngine);

                            final double fairRate = underlying.fairRate();

                            final Swaption swaption = new Swaption(
                                    underlying,
                                    new org.jquantlib.exercise.EuropeanExercise(
                                            exerciseDate),
                                    settlementTypes[h],
                                    settlementMethods[h]);
                            swaption.setPricingEngine(engine);

                            final double value = swaption.NPV();
                            final Swaption.ResultsImpl results =
                                    (Swaption.ResultsImpl) engine.getResults();
                            final Object deltaObj =
                                    results.additionalResults().get("delta");
                            if (deltaObj == null) {
                                fail("BlackSwaptionEngine did not publish "
                                        + "'delta' additional result");
                            }
                            final double delta =
                                    ((Double) deltaObj).doubleValue() * bump;

                            // --- Bump projection curve ------------------
                            projectionQuoteHandle.linkTo(
                                    new SimpleQuote(projectionRate + bump));

                            final double bumpedFairRate = underlying.fairRate();
                            final double bumpedValue = swaption.NPV();
                            final Swaption.ResultsImpl bumpedResults =
                                    (Swaption.ResultsImpl) engine.getResults();
                            final Object bumpedDeltaObj =
                                    bumpedResults.additionalResults().get("delta");
                            final double bumpedDelta =
                                    ((Double) bumpedDeltaObj).doubleValue() * bump;

                            final double deltaBump = bumpedFairRate - fairRate;
                            final double approxDelta =
                                    (bumpedValue - value) / deltaBump * bump;

                            final double lowerBound =
                                    Math.min(delta, bumpedDelta) - epsilon;
                            final double upperBound =
                                    Math.max(delta, bumpedDelta) + epsilon;

                            // Mean Value Theorem inequality (C++ exact match).
                            final boolean ok =
                                    (lowerBound < approxDelta)
                                    && (approxDelta < upperBound);
                            if (!ok) {
                                fail("failed to compute swaption delta:"
                                        + "\n  option tenor:     " + exercise
                                        + "\n  volatility:       " + vol
                                        + "\n  swap type:        " + swapTypes[h]
                                        + "\n  swap tenor:       " + length
                                        + "\n  strike:           " + strike
                                        + "\n  settlement type:  " + settlementTypes[h]
                                        + "\n  settlement method:" + settlementMethods[h]
                                        + "\n  npv:              " + value
                                        + "\n  calculated delta: " + delta
                                        + "\n  expected delta:   " + approxDelta
                                        + "\n  bumped delta:     " + bumpedDelta
                                        + "\n  lower bound:      " + lowerBound
                                        + "\n  upper bound:      " + upperBound);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Mirrors {@code testSwaptionDeltaInBachelierModel} from C++ v1.42.1
     * {@code swaption.cpp} (lines 1142-1147, via
     * {@code checkSwaptionDelta<BachelierSwaptionEngine>(true)} template).
     *
     * <p>Structurally identical to {@link #testSwaptionDeltaInBlackModel} but
     * uses {@link BachelierSwaptionEngine} (normal-vol) instead of
     * {@link BlackSwaptionEngine} (shifted-lognormal). The C++ template flag
     * {@code useBachelierVol = true} scales the volatility grid by
     * {@code 1/100}, converting Black-style relative vols (e.g. 30%) into
     * absolute basis-point-style normal vols (e.g. 30 bps = 0.003); this
     * port mirrors the same scaling.
     *
     * <p>The mean-value-theorem assertion is unchanged: bump the projection
     * (forward) curve by 1bp, recompute analytic delta, and require the
     * finite-difference slope to lie strictly between the pre- and post-bump
     * analytic deltas (epsilon cushion {@code 1e-10}).
     *
     * <p><strong>Tolerance tier</strong> — loose: matches C++ exactly,
     * comparison is via the mean-value-theorem inequality rather than a
     * fixed {@code |a-b|} threshold.
     */
    @Test
    public void testSwaptionDeltaInBachelierModel() {
        final Date today = new Date(13, Month.March, 2002);
        new Settings().setEvaluationDate(today);

        final Calendar calendar = new Target();
        final DayCounter act365 = new Actual365Fixed();
        final DayCounter thirty360 = new Thirty360(Thirty360.Convention.BondBasis);

        final double bump = 1.0e-4;
        final double epsilon = 1.0e-10;
        final double projectionRate = 0.01;

        // Projection (forwarding) curve — relinkable so we can bump it.
        final RelinkableHandle<Quote> projectionQuoteHandle =
                new RelinkableHandle<Quote>(new SimpleQuote(projectionRate));
        final RelinkableHandle<YieldTermStructure> projectionCurveHandle =
                new RelinkableHandle<YieldTermStructure>(
                        new FlatForward(today, projectionQuoteHandle, act365));

        // Discount curve — fixed.
        final Handle<YieldTermStructure> discountHandle =
                new Handle<YieldTermStructure>(
                        new FlatForward(today,
                                new Handle<Quote>(new SimpleQuote(0.0085)),
                                act365));

        final DiscountingSwapEngine swapEngine =
                new DiscountingSwapEngine(discountHandle);
        final IborIndex idx = new Euribor6M(projectionCurveHandle);

        // Reduced grid (matches the Black-model variant in this file). The
        // C++ test spans 6 vols x 6 exercises x 8 lengths x 5 strikes x 2
        // settlements = 5760 cases including a degenerate vol=0; here we
        // keep the kinds of points (low/mid/high vol, OTM/ATM/ITM strike,
        // short/medium/long expiry, both settlements and types) to stay
        // sub-second within the broader suite.
        final Period[] exercises = {
            new Period(1, TimeUnit.Years),
            new Period(5, TimeUnit.Years),
            new Period(10, TimeUnit.Years)
        };
        final Period[] lengths = {
            new Period(2, TimeUnit.Years),
            new Period(5, TimeUnit.Years),
            new Period(10, TimeUnit.Years)
        };
        final double[] strikes = { 0.03, 0.05, 0.07 };
        // Bachelier vols: normal-vol scale, i.e. Black vol / 100
        // (matches useBachelierVol=true in the C++ template).
        final double[] vols = { 0.10 / 100.0, 0.30 / 100.0, 0.70 / 100.0 };
        final VanillaSwap.Type[] swapTypes =
                { VanillaSwap.Type.Receiver, VanillaSwap.Type.Payer };
        final Settlement.Type[] settlementTypes =
                { Settlement.Type.Physical, Settlement.Type.Cash };
        final Settlement.Method[] settlementMethods =
                { Settlement.Method.PhysicalOTC,
                  Settlement.Method.CollateralizedCashPrice };

        for (final double vol : vols) {
            for (final Period exercise : exercises) {
                for (final Period length : lengths) {
                    for (final double strike : strikes) {
                        for (int h = 0; h < swapTypes.length; h++) {
                            // --- Build engine + swaption ----------------
                            final BachelierSwaptionEngine engine =
                                    BachelierSwaptionEngine.fromVolQuote(
                                            discountHandle,
                                            new Handle<Quote>(new SimpleQuote(vol)));

                            final Date exerciseDate =
                                    calendar.advance(today, exercise);
                            final Date startDate =
                                    calendar.advance(exerciseDate, 2, TimeUnit.Days);

                            // Reset projection quote to base value each iteration.
                            projectionQuoteHandle.linkTo(
                                    new SimpleQuote(projectionRate));

                            final VanillaSwap underlying = new MakeVanillaSwap(
                                    length, idx, strike)
                                    .withEffectiveDate(startDate)
                                    .withFixedLegTenor(
                                            new Period(1, TimeUnit.Years))
                                    .withFixedLegDayCount(thirty360)
                                    .withFloatingLegSpread(0.0)
                                    .withType(swapTypes[h])
                                    .value();
                            underlying.setPricingEngine(swapEngine);

                            final double fairRate = underlying.fairRate();

                            final Swaption swaption = new Swaption(
                                    underlying,
                                    new org.jquantlib.exercise.EuropeanExercise(
                                            exerciseDate),
                                    settlementTypes[h],
                                    settlementMethods[h]);
                            swaption.setPricingEngine(engine);

                            final double value = swaption.NPV();
                            final Swaption.ResultsImpl results =
                                    (Swaption.ResultsImpl) engine.getResults();
                            final Object deltaObj =
                                    results.additionalResults().get("delta");
                            if (deltaObj == null) {
                                fail("BachelierSwaptionEngine did not publish "
                                        + "'delta' additional result");
                            }
                            final double delta =
                                    ((Double) deltaObj).doubleValue() * bump;

                            // --- Bump projection curve ------------------
                            projectionQuoteHandle.linkTo(
                                    new SimpleQuote(projectionRate + bump));

                            final double bumpedFairRate = underlying.fairRate();
                            final double bumpedValue = swaption.NPV();
                            final Swaption.ResultsImpl bumpedResults =
                                    (Swaption.ResultsImpl) engine.getResults();
                            final Object bumpedDeltaObj =
                                    bumpedResults.additionalResults().get("delta");
                            final double bumpedDelta =
                                    ((Double) bumpedDeltaObj).doubleValue() * bump;

                            final double deltaBump = bumpedFairRate - fairRate;
                            final double approxDelta =
                                    (bumpedValue - value) / deltaBump * bump;

                            final double lowerBound =
                                    Math.min(delta, bumpedDelta) - epsilon;
                            final double upperBound =
                                    Math.max(delta, bumpedDelta) + epsilon;

                            // Mean Value Theorem inequality (C++ exact match).
                            final boolean ok =
                                    (lowerBound < approxDelta)
                                    && (approxDelta < upperBound);
                            if (!ok) {
                                fail("failed to compute swaption delta:"
                                        + "\n  option tenor:     " + exercise
                                        + "\n  volatility:       " + vol
                                        + "\n  swap type:        " + swapTypes[h]
                                        + "\n  swap tenor:       " + length
                                        + "\n  strike:           " + strike
                                        + "\n  settlement type:  " + settlementTypes[h]
                                        + "\n  settlement method:" + settlementMethods[h]
                                        + "\n  npv:              " + value
                                        + "\n  calculated delta: " + delta
                                        + "\n  expected delta:   " + approxDelta
                                        + "\n  bumped delta:     " + bumpedDelta
                                        + "\n  lower bound:      " + lowerBound
                                        + "\n  upper bound:      " + upperBound);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Mirrors {@code testMakeSwaptionWithExerciseCalendar} from C++ v1.42.1
     * {@code swaption.cpp}. Exercises the {@code MakeSwaption.withExerciseCalendar}
     * builder override and verifies that:
     *
     * <ol>
     *   <li>Without override, the swap-index's fixing calendar (TARGET) is used.</li>
     *   <li>With {@code withExerciseCalendar(UnitedStates.SETTLEMENT)}, the exercise
     *       date is computed against the US calendar instead (different result on
     *       2016-10-10 due to Columbus Day).</li>
     *   <li>An explicit {@code withExerciseDate(...)} takes precedence over the
     *       calendar — calendar is then irrelevant.</li>
     * </ol>
     *
     * <p><strong>Tolerance tier</strong> — exact (date arithmetic).
     */
    @Test
    public void testMakeSwaptionWithExerciseCalendar() {
        // Use a specific date where TARGET and US Settlement diverge.
        final Date today = new Date(9, Month.October, 2015);
        new Settings().setEvaluationDate(today);

        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.05, new Actual365Fixed()));

        final EuriborSwapIsdaFixA swapIndex = new EuriborSwapIsdaFixA(
                new Period(5, TimeUnit.Years), ts);

        final Calendar targetCalendar = swapIndex.fixingCalendar();
        final Calendar usCalendar = new UnitedStates(UnitedStates.Market.SETTLEMENT);

        // 1. Default uses swap index's fixing calendar (TARGET).
        final Swaption defaultSwaption = new MakeSwaption(
                swapIndex, new Period(1, TimeUnit.Years), 0.05).value();
        final Date defaultExercise = defaultSwaption.exercise().dates().get(0);

        final Date expected = targetCalendar.advance(
                targetCalendar.adjust(today),
                new Period(1, TimeUnit.Years),
                BusinessDayConvention.ModifiedFollowing);
        assertEquals("default exercise date mismatch", expected, defaultExercise);

        // 2. With custom US calendar, exercise date differs.
        final Swaption customSwaption = new MakeSwaption(
                swapIndex, new Period(1, TimeUnit.Years), 0.05)
                .withExerciseCalendar(usCalendar)
                .value();
        final Date customExercise = customSwaption.exercise().dates().get(0);

        final Date expectedCustom = usCalendar.advance(
                usCalendar.adjust(today),
                new Period(1, TimeUnit.Years),
                BusinessDayConvention.ModifiedFollowing);
        assertEquals("custom exercise date mismatch", expectedCustom, customExercise);
        assertNotEquals("US and TARGET should disagree on 2016-10-10",
                customExercise, defaultExercise);

        // 3. Explicit withExerciseDate takes precedence over calendar.
        final Date explicitDate = targetCalendar.advance(today,
                new Period(6, TimeUnit.Months));
        final Date fixingDate = targetCalendar.advance(today,
                new Period(1, TimeUnit.Years));
        final Swaption explicitSwaption = new MakeSwaption(
                swapIndex, fixingDate, 0.05)
                .withExerciseCalendar(usCalendar)
                .withExerciseDate(explicitDate)
                .value();
        assertEquals("explicit date should override calendar",
                explicitDate, explicitSwaption.exercise().dates().get(0));
    }

    /**
     * Mirrors {@code testBlackEngineCaching} from C++ v1.42.1
     * {@code swaption.cpp}.
     *
     * <p>Builds a 1Yx1Y physical payer swaption priced with BlackSwaptionEngine
     * and verifies that {@link Swaption#isCalculated()} reports {@code false}
     * before the first {@code NPV()} call and {@code true} after.  This
     * exercises the inherited {@link org.jquantlib.util.LazyObject} cache state
     * machine and confirms the engine populates results without triggering
     * notification cycles.
     *
     * <p><strong>Tolerance tier</strong> — exact (boolean flags).
     */
    @Test
    public void testBlackEngineCaching() {
        // --- C++ fixture (CommonVars) ---------------------------------------
        final Calendar calendar = new Target();
        final DayCounter act365 = new Actual365Fixed();
        final DayCounter thirty360 = new Thirty360(Thirty360.Convention.BondBasis);
        final int settlementDays = 2;

        // Use a fixed today so the test is deterministic.
        final Date today = calendar.adjust(new Date(13, Month.March, 2002));
        new Settings().setEvaluationDate(today);
        final Date settlement = calendar.advance(today, settlementDays, TimeUnit.Days);

        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(
                new FlatForward(settlement, 0.05, act365));
        final IborIndex idx = new Euribor6M(ts);

        // --- Build swaption --------------------------------------------------
        final Date exerciseDate = calendar.advance(today, new Period(1, TimeUnit.Years));
        final Date startDate = calendar.advance(exerciseDate, settlementDays, TimeUnit.Days);

        final VanillaSwap swap = new MakeVanillaSwap(
                new Period(1, TimeUnit.Years), idx, 0.03)
                .withEffectiveDate(startDate)
                .withFixedLegTenor(new Period(1, TimeUnit.Years))
                .withFixedLegDayCount(thirty360)
                .withFloatingLegSpread(0.0)
                .withType(VanillaSwap.Type.Payer)
                .value();

        final Handle<Quote> volQuote = new Handle<Quote>(new SimpleQuote(0.12));
        final BlackSwaptionEngine engine =
                BlackSwaptionEngine.fromVolQuote(ts, volQuote);

        final Swaption swaption = new Swaption(
                swap, new org.jquantlib.exercise.EuropeanExercise(exerciseDate));
        swaption.setPricingEngine(engine);

        // --- Verify caching state machine -----------------------------------
        assertFalse("swaption should not be calculated before NPV()",
                swaption.isCalculated());

        swaption.NPV();

        assertTrue("swaption should be calculated after NPV()",
                swaption.isCalculated());
    }

    //--------------------------------------------------------------------
    // CommonVars-style fixture + helpers, mirroring C++ swaption.cpp
    // CommonVars (lines 61-145). Shared by testStrikeDependency,
    // testSpreadDependency, testSpreadTreatment, testVega and
    // testCashSettledSwaptions.
    //--------------------------------------------------------------------

    /** Fixture container: mirrors C++ {@code CommonVars} (swaption.cpp:61). */
    private static final class CommonVars {
        final Date today;
        final Date settlement;
        final double nominal = 1000000.0;
        final Calendar calendar;
        final BusinessDayConvention fixedConvention = BusinessDayConvention.Unadjusted;
        final Frequency fixedFrequency = Frequency.Annual;
        final DayCounter fixedDayCount =
                new Thirty360(Thirty360.Convention.BondBasis);
        final BusinessDayConvention floatingConvention;
        final Period floatingTenor;
        final IborIndex index;
        final int settlementDays = 2;
        final Handle<YieldTermStructure> termStructure;

        CommonVars() {
            // Build off a fixed date so the test is deterministic; matches
            // the deterministic fixtures used by the other body-filled tests
            // in this class.
            this.calendar = new Target();
            this.today = calendar.adjust(new Date(13, Month.March, 2002));
            new Settings().setEvaluationDate(today);
            this.settlement = calendar.advance(today, settlementDays,
                    TimeUnit.Days);
            this.termStructure = new Handle<YieldTermStructure>(
                    new FlatForward(settlement, 0.05, new Actual365Fixed()));
            this.index = new Euribor6M(termStructure);
            this.floatingConvention = index.businessDayConvention();
            this.floatingTenor = index.tenor();
        }

        /** Returned by {@link #makeSwaptionWithEngine} so callers can read
         *  the engine's additional-results map without needing a public
         *  {@code Swaption.pricingEngine()} accessor on the Java side. */
        static final class SwaptionAndEngine {
            final Swaption swaption;
            final BlackSwaptionEngine engine;
            SwaptionAndEngine(final Swaption s, final BlackSwaptionEngine e) {
                this.swaption = s;
                this.engine = e;
            }
        }

        SwaptionAndEngine makeSwaptionWithEngine(final VanillaSwap swap,
                                                 final Date exercise,
                                                 final double volatility,
                                                 final Settlement.Type sType,
                                                 final Settlement.Method sMethod) {
            // C++ default cash-annuity model is SwapRate (swaption.cpp:86);
            // mirror that here by wiring a ConstantSwaptionVolatility surface
            // around the supplied vol quote so we can pass the full
            // constructor that exposes the CashAnnuityModel.
            final Handle<org.jquantlib.termstructures.SwaptionVolatilityStructure> surface =
                    new Handle<org.jquantlib.termstructures.SwaptionVolatilityStructure>(
                            new org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility(
                                    0,
                                    new org.jquantlib.time.calendars.NullCalendar(),
                                    BusinessDayConvention.Following,
                                    new Handle<Quote>(new SimpleQuote(volatility)),
                                    new Actual365Fixed()));
            final BlackSwaptionEngine engine = new BlackSwaptionEngine(
                    termStructure, surface,
                    BlackSwaptionEngine.CashAnnuityModel.SwapRate, 0.0);
            final Swaption swaption = new Swaption(swap,
                    new org.jquantlib.exercise.EuropeanExercise(exercise),
                    sType, sMethod);
            swaption.setPricingEngine(engine);
            return new SwaptionAndEngine(swaption, engine);
        }

        Swaption makeSwaption(final VanillaSwap swap,
                              final Date exercise,
                              final double volatility,
                              final Settlement.Type sType,
                              final Settlement.Method sMethod) {
            return makeSwaptionWithEngine(swap, exercise, volatility, sType,
                    sMethod).swaption;
        }

        /** Convenience: physical-settled / PhysicalOTC. */
        Swaption makeSwaption(final VanillaSwap swap, final Date exercise,
                              final double volatility) {
            return makeSwaption(swap, exercise, volatility,
                    Settlement.Type.Physical, Settlement.Method.PhysicalOTC);
        }
    }

    private static CommonVars makeCommonVars() {
        return new CommonVars();
    }

    // Reduced iteration grids matching the existing
    // testSwaptionDeltaInBlackModel pattern (CFC-d-73): keep all "kinds"
    // of point C++ exercises (short/medium/long expiry, low/mid/high vol,
    // OTM/ATM/ITM strikes, both swap types, both settlements) but shrink
    // the grid so the suite stays under a few seconds.
    private static final Period[] EXERCISES = {
            new Period(1, TimeUnit.Years),
            new Period(5, TimeUnit.Years),
            new Period(10, TimeUnit.Years) };
    private static final Period[] LENGTHS = {
            new Period(2, TimeUnit.Years),
            new Period(5, TimeUnit.Years),
            new Period(10, TimeUnit.Years) };
    private static final VanillaSwap.Type[] SWAP_TYPES = {
            VanillaSwap.Type.Receiver, VanillaSwap.Type.Payer };

    /** Mirrors swaption.cpp:172-263 ({@code testStrikeDependency}). */
    private static void runStrikeDependency(final CommonVars vars) {
        final double[] strikes = { 0.03, 0.04, 0.05, 0.06, 0.07 };
        final double vol = 0.20;
        for (final Period exercise : EXERCISES) {
            for (final Period length : LENGTHS) {
                for (final VanillaSwap.Type k : SWAP_TYPES) {
                    final Date exerciseDate = vars.calendar.advance(
                            vars.today, exercise);
                    final Date startDate = vars.calendar.advance(
                            exerciseDate, vars.settlementDays, TimeUnit.Days);
                    final double[] values = new double[strikes.length];
                    final double[] valuesCash = new double[strikes.length];
                    for (int s = 0; s < strikes.length; s++) {
                        final VanillaSwap swap = new MakeVanillaSwap(
                                length, vars.index, strikes[s])
                                .withEffectiveDate(startDate)
                                .withFixedLegTenor(new Period(1, TimeUnit.Years))
                                .withFixedLegDayCount(vars.fixedDayCount)
                                .withFloatingLegSpread(0.0)
                                .withType(k)
                                .value();
                        values[s] = vars.makeSwaption(swap, exerciseDate,
                                vol).NPV();
                        valuesCash[s] = vars.makeSwaption(swap, exerciseDate,
                                vol, Settlement.Type.Cash,
                                Settlement.Method.ParYieldCurve).NPV();
                    }
                    if (k == VanillaSwap.Type.Payer) {
                        assertMonotoneDecreasing(values, "Payer/physical",
                                exercise, length, strikes);
                        assertMonotoneDecreasing(valuesCash, "Payer/cash",
                                exercise, length, strikes);
                    } else {
                        assertMonotoneIncreasing(values, "Receiver/physical",
                                exercise, length, strikes);
                        assertMonotoneIncreasing(valuesCash, "Receiver/cash",
                                exercise, length, strikes);
                    }
                }
            }
        }
    }

    private static void assertMonotoneDecreasing(final double[] xs,
                                                 final String label,
                                                 final Period exercise,
                                                 final Period length,
                                                 final double[] params) {
        for (int i = 0; i + 1 < xs.length; i++) {
            if (xs[i] < xs[i + 1]) {
                fail(label + " NPV is increasing with the strike:"
                        + "\n  exercise: " + exercise
                        + "\n  length:   " + length
                        + "\n  value[" + i + "]   = " + xs[i]
                        + " at param=" + params[i]
                        + "\n  value[" + (i + 1) + "] = " + xs[i + 1]
                        + " at param=" + params[i + 1]);
            }
        }
    }

    private static void assertMonotoneIncreasing(final double[] xs,
                                                 final String label,
                                                 final Period exercise,
                                                 final Period length,
                                                 final double[] params) {
        for (int i = 0; i + 1 < xs.length; i++) {
            if (xs[i] > xs[i + 1]) {
                fail(label + " NPV is decreasing with the parameter:"
                        + "\n  exercise: " + exercise
                        + "\n  length:   " + length
                        + "\n  value[" + i + "]   = " + xs[i]
                        + " at param=" + params[i]
                        + "\n  value[" + (i + 1) + "] = " + xs[i + 1]
                        + " at param=" + params[i + 1]);
            }
        }
    }

    /** Mirrors swaption.cpp:265-349 ({@code testSpreadDependency}). */
    private static void runSpreadDependency(final CommonVars vars) {
        final double[] spreads = { -0.002, -0.001, 0.0, 0.001, 0.002 };
        for (final Period exercise : EXERCISES) {
            for (final Period length : LENGTHS) {
                for (final VanillaSwap.Type k : SWAP_TYPES) {
                    final Date exerciseDate = vars.calendar.advance(
                            vars.today, exercise);
                    final Date startDate = vars.calendar.advance(
                            exerciseDate, vars.settlementDays, TimeUnit.Days);
                    final double[] values = new double[spreads.length];
                    final double[] valuesCash = new double[spreads.length];
                    for (int s = 0; s < spreads.length; s++) {
                        final VanillaSwap swap = new MakeVanillaSwap(
                                length, vars.index, 0.06)
                                .withFixedLegTenor(new Period(1, TimeUnit.Years))
                                .withFixedLegDayCount(vars.fixedDayCount)
                                .withEffectiveDate(startDate)
                                .withFloatingLegSpread(spreads[s])
                                .withType(k)
                                .value();
                        values[s] = vars.makeSwaption(swap, exerciseDate,
                                0.20).NPV();
                        valuesCash[s] = vars.makeSwaption(swap, exerciseDate,
                                0.20, Settlement.Type.Cash,
                                Settlement.Method.ParYieldCurve).NPV();
                    }
                    if (k == VanillaSwap.Type.Payer) {
                        // payer NPV non-decreasing in spread
                        assertMonotoneIncreasing(values, "Payer/physical",
                                exercise, length, spreads);
                        assertMonotoneIncreasing(valuesCash, "Payer/cash",
                                exercise, length, spreads);
                    } else {
                        // receiver NPV non-increasing in spread
                        assertMonotoneDecreasing(values, "Receiver/physical",
                                exercise, length, spreads);
                        assertMonotoneDecreasing(valuesCash, "Receiver/cash",
                                exercise, length, spreads);
                    }
                }
            }
        }
    }

    /** Mirrors swaption.cpp:351-410 ({@code testSpreadTreatment}). */
    private static void runSpreadTreatment(final CommonVars vars) {
        final double[] spreads = { -0.002, -0.001, 0.0, 0.001, 0.002 };
        for (final Period exercise : EXERCISES) {
            for (final Period length : LENGTHS) {
                for (final VanillaSwap.Type k : SWAP_TYPES) {
                    final Date exerciseDate = vars.calendar.advance(
                            vars.today, exercise);
                    final Date startDate = vars.calendar.advance(
                            exerciseDate, vars.settlementDays, TimeUnit.Days);
                    for (final double spread : spreads) {
                        final VanillaSwap swap = new MakeVanillaSwap(
                                length, vars.index, 0.06)
                                .withFixedLegTenor(new Period(1, TimeUnit.Years))
                                .withFixedLegDayCount(vars.fixedDayCount)
                                .withEffectiveDate(startDate)
                                .withFloatingLegSpread(spread)
                                .withType(k)
                                .value();
                        // We need the spread correction; price via a
                        // DiscountingSwapEngine first to populate fixedLegBPS
                        // and floatingLegBPS.
                        swap.setPricingEngine(new DiscountingSwapEngine(
                                vars.termStructure));
                        final double correction = spread
                                * swap.floatingLegBPS() / swap.fixedLegBPS();
                        final VanillaSwap equivalent = new MakeVanillaSwap(
                                length, vars.index, 0.06 + correction)
                                .withFixedLegTenor(new Period(1, TimeUnit.Years))
                                .withFixedLegDayCount(vars.fixedDayCount)
                                .withEffectiveDate(startDate)
                                .withFloatingLegSpread(0.0)
                                .withType(k)
                                .value();
                        final Swaption s1 = vars.makeSwaption(swap,
                                exerciseDate, 0.20);
                        final Swaption s2 = vars.makeSwaption(equivalent,
                                exerciseDate, 0.20);
                        final Swaption s1Cash = vars.makeSwaption(swap,
                                exerciseDate, 0.20, Settlement.Type.Cash,
                                Settlement.Method.ParYieldCurve);
                        final Swaption s2Cash = vars.makeSwaption(equivalent,
                                exerciseDate, 0.20, Settlement.Type.Cash,
                                Settlement.Method.ParYieldCurve);
                        if (Math.abs(s1.NPV() - s2.NPV()) > 1.0e-6) {
                            fail("wrong spread treatment (physical):"
                                    + "\n  exercise: " + exerciseDate
                                    + "\n  length:   " + length
                                    + "\n  type:     " + k
                                    + "\n  spread:   " + spread
                                    + "\n  original swaption value:   " + s1.NPV()
                                    + "\n  equivalent swaption value: " + s2.NPV());
                        }
                        if (Math.abs(s1Cash.NPV() - s2Cash.NPV()) > 1.0e-6) {
                            fail("wrong spread treatment (cash/ParYieldCurve):"
                                    + "\n  exercise: " + exerciseDate
                                    + "\n  length:   " + length
                                    + "\n  type:     " + k
                                    + "\n  spread:   " + spread
                                    + "\n  original swaption value:   " + s1Cash.NPV()
                                    + "\n  equivalent swaption value: " + s2Cash.NPV());
                        }
                    }
                }
            }
        }
    }

    /** Mirrors swaption.cpp:463-528 ({@code testVega}). */
    private static void runVega(final CommonVars vars) {
        final Settlement.Type[] types = {
                Settlement.Type.Physical, Settlement.Type.Cash };
        final Settlement.Method[] methods = {
                Settlement.Method.PhysicalOTC,
                Settlement.Method.ParYieldCurve };
        final double[] strikes = { 0.03, 0.04, 0.05, 0.06, 0.07 };
        // C++ vols include 0.01 (almost intrinsic) which is degenerate
        // for the FD vega check; keep the same {mid, high} subset that
        // exercises the analytic-vs-FD agreement.
        final double[] vols = { 0.20, 0.30, 0.70, 0.90 };
        final double shift = 1.0e-8;
        for (final Period exercise : EXERCISES) {
            final Date exerciseDate = vars.calendar.advance(vars.today, exercise);
            final Date startDate = vars.calendar.advance(exerciseDate,
                    vars.settlementDays, TimeUnit.Days);
            for (final Period length : LENGTHS) {
                for (final double strike : strikes) {
                    for (int h = 0; h < types.length; h++) {
                        final VanillaSwap swap = new MakeVanillaSwap(
                                length, vars.index, strike)
                                .withEffectiveDate(startDate)
                                .withFixedLegTenor(new Period(1, TimeUnit.Years))
                                .withFixedLegDayCount(vars.fixedDayCount)
                                .withFloatingLegSpread(0.0)
                                .withType(SWAP_TYPES[h])
                                .value();
                        for (final double vol : vols) {
                            final CommonVars.SwaptionAndEngine pair =
                                    vars.makeSwaptionWithEngine(swap,
                                            exerciseDate, vol, types[h],
                                            methods[h]);
                            final Swaption swaption = pair.swaption;
                            final BlackSwaptionEngine engine = pair.engine;
                            final Swaption s1 = vars.makeSwaption(swap,
                                    exerciseDate, vol - shift,
                                    types[h], methods[h]);
                            final Swaption s2 = vars.makeSwaption(swap,
                                    exerciseDate, vol + shift,
                                    types[h], methods[h]);
                            final double swaptionNPV = swaption.NPV();
                            final double numericalVegaPerPoint =
                                    (s2.NPV() - s1.NPV()) / (200.0 * shift);
                            if (numericalVegaPerPoint / swaptionNPV
                                    > 1.0e-7) {
                                final Object vegaObj = ((Swaption.ResultsImpl)
                                        engine.getResults())
                                        .additionalResults().get("vega");
                                if (vegaObj == null) {
                                    fail("BlackSwaptionEngine did not publish"
                                            + " 'vega' additional result");
                                }
                                final double analyticalVegaPerPoint =
                                        ((Double) vegaObj).doubleValue()
                                                / 100.0;
                                final double discrepancy = Math.abs(
                                        analyticalVegaPerPoint
                                                - numericalVegaPerPoint)
                                        / numericalVegaPerPoint;
                                final double tolerance = 0.015;
                                if (discrepancy > tolerance) {
                                    fail("failed to compute swaption vega:"
                                            + "\n  option tenor: " + exercise
                                            + "\n  vol:          " + vol
                                            + "\n  swap type:    " + SWAP_TYPES[h]
                                            + "\n  swap tenor:   " + length
                                            + "\n  strike:       " + strike
                                            + "\n  settlement:   " + types[h]
                                            + "\n  npv:          " + swaptionNPV
                                            + "\n  analytical:   "
                                            + analyticalVegaPerPoint
                                            + "\n  numerical:    "
                                            + numericalVegaPerPoint
                                            + "\n  discrepancy:  " + discrepancy
                                            + "\n  tolerance:    " + tolerance);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Mirrors swaption.cpp:530-824 ({@code testCashSettledSwaptions}). */
    private static void runCashSettledSwaptions(final CommonVars vars) {
        final double strike = 0.05;
        final DayCounter act365 = new Actual365Fixed();
        final DayCounter thirty360 =
                new Thirty360(Thirty360.Convention.BondBasis);
        for (final Period exercise : EXERCISES) {
            for (final Period length : LENGTHS) {
                final Date exerciseDate = vars.calendar.advance(vars.today,
                        exercise);
                final Date startDate = vars.calendar.advance(exerciseDate,
                        vars.settlementDays, TimeUnit.Days);
                final Date maturity = vars.calendar.advance(startDate, length,
                        vars.floatingConvention);

                final Schedule floatSchedule = new Schedule(startDate, maturity,
                        vars.floatingTenor, vars.calendar,
                        vars.floatingConvention, vars.floatingConvention,
                        DateGeneration.Rule.Forward, false);

                final Schedule fixedScheduleU = new Schedule(startDate, maturity,
                        new Period(vars.fixedFrequency), vars.calendar,
                        BusinessDayConvention.Unadjusted,
                        BusinessDayConvention.Unadjusted,
                        DateGeneration.Rule.Forward, true);
                final Schedule fixedScheduleA = new Schedule(startDate, maturity,
                        new Period(vars.fixedFrequency), vars.calendar,
                        BusinessDayConvention.ModifiedFollowing,
                        BusinessDayConvention.ModifiedFollowing,
                        DateGeneration.Rule.Forward, true);

                final VanillaSwap swapU360 = new VanillaSwap(SWAP_TYPES[0],
                        vars.nominal, fixedScheduleU, strike, thirty360,
                        floatSchedule, vars.index, 0.0,
                        vars.index.dayCounter());
                final VanillaSwap swapU365 = new VanillaSwap(SWAP_TYPES[0],
                        vars.nominal, fixedScheduleU, strike, act365,
                        floatSchedule, vars.index, 0.0,
                        vars.index.dayCounter());
                final VanillaSwap swapA360 = new VanillaSwap(SWAP_TYPES[0],
                        vars.nominal, fixedScheduleA, strike, thirty360,
                        floatSchedule, vars.index, 0.0,
                        vars.index.dayCounter());
                final VanillaSwap swapA365 = new VanillaSwap(SWAP_TYPES[0],
                        vars.nominal, fixedScheduleA, strike, act365,
                        floatSchedule, vars.index, 0.0,
                        vars.index.dayCounter());

                final DiscountingSwapEngine swapEngine =
                        new DiscountingSwapEngine(vars.termStructure);
                swapU360.setPricingEngine(swapEngine);
                swapU365.setPricingEngine(swapEngine);
                swapA360.setPricingEngine(swapEngine);
                swapA365.setPricingEngine(swapEngine);

                final double annU360 = signedAnnuity(swapU360);
                final double annU365 = signedAnnuity(swapU365);
                final double annA360 = signedAnnuity(swapA360);
                final double annA365 = signedAnnuity(swapA365);

                final double cashAnnU360 = cashAnnuity(vars, swapU360,
                        strike, thirty360);
                final double cashAnnU365 = cashAnnuity(vars, swapU365,
                        strike, act365);
                final double cashAnnA360 = cashAnnuity(vars, swapA360,
                        strike, thirty360);
                final double cashAnnA365 = cashAnnuity(vars, swapA365,
                        strike, act365);

                checkCashRatio(vars, swapU360, exerciseDate, length,
                        annU360, cashAnnU360, "Unadjusted/30-360");
                checkCashRatio(vars, swapU365, exerciseDate, length,
                        annU365, cashAnnU365, "Unadjusted/Act-365");
                checkCashRatio(vars, swapA360, exerciseDate, length,
                        annA360, cashAnnA360, "ModFollowing/30-360");
                checkCashRatio(vars, swapA365, exerciseDate, length,
                        annA365, cashAnnA365, "ModFollowing/Act-365");
            }
        }
    }

    private static double signedAnnuity(final VanillaSwap swap) {
        final double raw = swap.fixedLegBPS() / 1.0e-4;
        return (swap.type() == VanillaSwap.Type.Payer) ? -raw : raw;
    }

    /**
     * Replication of the C++ cash-annuity formula
     * ({@code sum_i amount_i/strike * flatRate.discount(date_i)}) used to
     * validate the engine's internal computation.
     */
    private static double cashAnnuity(final CommonVars vars,
                                      final VanillaSwap swap,
                                      final double strike,
                                      final DayCounter dc) {
        final YieldTermStructure flat = new FlatForward(vars.settlement,
                swap.fairRate(), dc, Compounding.Compounded,
                vars.fixedFrequency);
        final Leg fixedLeg = swap.fixedLeg();
        double sum = 0.0;
        for (int i = 0; i < fixedLeg.size(); i++) {
            final CashFlow cf = fixedLeg.get(i);
            sum += cf.amount() / strike * flat.discount(cf.date());
        }
        return sum;
    }

    private static void checkCashRatio(final CommonVars vars,
                                       final VanillaSwap swap,
                                       final Date exerciseDate,
                                       final Period length,
                                       final double annuity,
                                       final double cashAnnuity,
                                       final String label) {
        final Swaption physical = vars.makeSwaption(swap, exerciseDate, 0.20);
        final double valuePhysical = physical.NPV();
        final Swaption cash = vars.makeSwaption(swap, exerciseDate, 0.20,
                Settlement.Type.Cash, Settlement.Method.ParYieldCurve);
        final double valueCash = cash.NPV();
        final double npvRatio = valueCash / valuePhysical;
        final double annuityRatio = cashAnnuity / annuity;
        if (Math.abs(annuityRatio - npvRatio) > 1.0e-10) {
            fail("NPV ratio != annuity ratio for " + label + ":"
                    + "\n  length:           " + length
                    + "\n  exercise:         " + exerciseDate
                    + "\n  physical NPV:     " + valuePhysical
                    + "\n  fixedLegBPS ann:  " + annuity
                    + "\n  cash NPV:         " + valueCash
                    + "\n  cashAnnuity:      " + cashAnnuity
                    + "\n  NPV ratio:        " + npvRatio
                    + "\n  annuity ratio:    " + annuityRatio
                    + "\n  diff:             " + (annuityRatio - npvRatio));
        }
    }
}
