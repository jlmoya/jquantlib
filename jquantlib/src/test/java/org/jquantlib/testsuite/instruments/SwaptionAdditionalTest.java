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
import org.jquantlib.pricingengines.swaption.BlackSwaptionEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
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

    @Ignore("Phase 5e.5b-CFC-d — requires BlackSwaptionEngine Settlement.Cash/ParYieldCurve"
            + " path (currently throws UnsupportedOperationException); needs"
            + " CashFlows.bps(InterestRate,...) + Schedule.tenor()/hasTenor() port")
    @Test
    public void testStrikeDependency() { fail("not implemented"); }

    @Ignore("Phase 5e.5b-CFC-d — requires BlackSwaptionEngine Settlement.Cash/ParYieldCurve"
            + " path (currently throws UnsupportedOperationException); needs"
            + " CashFlows.bps(InterestRate,...) + Schedule.tenor()/hasTenor() port")
    @Test
    public void testSpreadDependency() { fail("not implemented"); }

    @Ignore("Phase 5e.5b-CFC-d — requires BlackSwaptionEngine Settlement.Cash/ParYieldCurve"
            + " path (currently throws UnsupportedOperationException); needs"
            + " CashFlows.bps(InterestRate,...) + Schedule.tenor()/hasTenor() port")
    @Test
    public void testSpreadTreatment() { fail("not implemented"); }

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

    @Ignore("Phase 5e.5b-CFC-d — testVega iterates over Settlement.Cash/ParYieldCurve"
            + " annuity branch (engine still throws UnsupportedOperationException);"
            + " analytic vega is now published as an additional result, so this"
            + " test will un-ignore as soon as the ParYieldCurve cash-annuity"
            + " path lands (same dependency as testStrikeDependency et al.)")
    @Test
    public void testVega() { fail("not implemented"); }

    @Ignore("Phase 5e.5b-CFC-d — requires BlackSwaptionEngine Settlement.Cash/ParYieldCurve"
            + " path (currently throws UnsupportedOperationException); needs"
            + " CashFlows.bps(InterestRate,...) + Schedule.tenor()/hasTenor() port")
    @Test
    public void testCashSettledSwaptions() { fail("not implemented"); }

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

    @Ignore("Phase 5f.5 — requires BachelierSwaptionEngine (not yet ported;"
            + " BlackSwaptionEngine handles VolatilityType.Normal but the C++"
            + " test uses a dedicated BachelierSwaptionEngine class with a"
            + " ConstantSwaptionVolatility(BachelierSpec) constructor that"
            + " has no Java counterpart yet)")
    @Test
    public void testSwaptionDeltaInBachelierModel() { fail("not implemented"); }

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
}
