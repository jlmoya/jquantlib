/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.pricingengines.capfloor.BlackCapFloorEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/capfloor.cpp (Phase 5e).
 *
 * <p>10 BOOST_AUTO_TEST_CASE methods exercising the
 * {@link org.jquantlib.instruments.CapFloor} instrument with the
 * {@link BlackCapFloorEngine} pricing engine.
 *
 * <h3>Phase Body-Fill (2026-05-09)</h3>
 *
 * <p>The 4 structural tests (testStrikeDependency, testConsistency,
 * testParity, testATMRate) are body-filled and un-ignored.  These
 * exercise pure consistency / put-call parity / ATM-rate identities and
 * do not need cached reference values from C++ probes.
 *
 * <p>The remaining 6 tests stay deferred to Phase 5e.5:
 * <ul>
 *   <li>testVega — needs the analytical "vega" result-map entry on
 *       BlackCapFloorEngine plus numerical-bump cross-check;</li>
 *   <li>testImpliedVolatility — needs CapFloor.impliedVolatility() solver;</li>
 *   <li>testCachedValue — needs cached NPVs regenerated from C++ v1.42.1
 *       (could be added via migration-harness probes);</li>
 *   <li>testCachedValueFromOptionLets / testOptionLetsDelta — need
 *       CapFloor.optionletsPrice() / optionletsBPS() / optionletsDelta()
 *       result-map accessors (WI-5e.5-CF-3);</li>
 *   <li>testBachelierOptionLetsDelta — needs Bachelier-mode capfloor
 *       engine + optionletsDelta accessor (WI-5e.5-CF-2 + WI-5e.5-CF-3).</li>
 * </ul>
 */
public class CapFloorTest {

    public CapFloorTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Mirror of C++ {@code CommonVars} struct (capfloor.cpp:51-127). */
    private static final class CommonVars {
        final Date settlement;
        final double[] nominals;
        final BusinessDayConvention convention;
        final Frequency frequency;
        final IborIndex index;
        final Calendar calendar;
        final int fixingDays;
        final RelinkableHandle<YieldTermStructure> termStructure;

        CommonVars() {
            this.nominals = new double[] { 100.0 };
            this.frequency = Frequency.Semiannual;
            this.termStructure = new RelinkableHandle<YieldTermStructure>();
            this.index = new Euribor6M(termStructure);
            this.calendar = index.fixingCalendar();
            this.convention = BusinessDayConvention.ModifiedFollowing;
            final Date today = new Settings().evaluationDate();
            final int settlementDays = 2;
            this.fixingDays = 2;
            this.settlement = calendar.advance(today, settlementDays, TimeUnit.Days);
            this.termStructure.linkTo(Utilities.flatRate(settlement, 0.05,
                    new ActualActual(ActualActual.Convention.ISDA)));
        }

        Leg makeLeg(final Date startDate, final int length) {
            final Date endDate = calendar.advance(startDate,
                    new Period(length, TimeUnit.Years), convention);
            final Schedule schedule = new Schedule(startDate, endDate,
                    new Period(frequency), calendar,
                    convention, convention,
                    DateGeneration.Rule.Forward, false);
            return new IborLeg(schedule, index)
                    .withNotionals(new Array(nominals))
                    .withPaymentDayCounter(index.dayCounter())
                    .withPaymentAdjustment(convention)
                    .withFixingDays(fixingDays)
                    .Leg();
        }

        BlackCapFloorEngine makeEngine(final double volatility) {
            final Handle<Quote> vol = new Handle<Quote>(new SimpleQuote(volatility));
            return new BlackCapFloorEngine(termStructure, vol,
                    new ActualActual(ActualActual.Convention.ISDA));
        }

        CapFloor makeCapFloor(final CapFloor.Type type, final Leg leg,
                              final double strike, final double volatility) {
            final List<Double> strikes = new ArrayList<Double>(
                    Arrays.asList(Double.valueOf(strike)));
            final CapFloor cf;
            switch (type) {
                case Cap:
                    cf = new CapFloor(CapFloor.Type.Cap, leg, strikes,
                            termStructure, null);
                    break;
                case Floor:
                    cf = new CapFloor(CapFloor.Type.Floor, leg, strikes,
                            termStructure, null);
                    break;
                default:
                    throw new IllegalArgumentException("unknown cap/floor type");
            }
            cf.setPricingEngine(makeEngine(volatility));
            return cf;
        }
    }

    /**
     * Mirrors C++ capfloor.cpp testVega (lines 147-194). For each
     * (length, vol, strike, type), build a cap/floor priced with
     * {@link BlackCapFloorEngine}, then compare the analytic
     * {@code vega} additional result against a central finite-difference
     * estimate from two engines at {@code vol +/- 1e-8}. Java reads the
     * vega via the engine's {@link CapFloor.ResultsImpl#additionalResults()}
     * map directly (see {@link BlackCapFloorEngine#calculate()} which
     * populates the C++-equivalent named results — Phase 5e.5b-CFC-d-49).
     */
    @Test
    public void testVega() {
        QL.info("Testing cap/floor vega...");

        final CommonVars vars = new CommonVars();

        final int[] lengths = { 1, 2, 3, 4, 5, 6, 7, 10, 15, 20, 30 };
        final double[] vols = { 0.01, 0.05, 0.10, 0.15, 0.20 };
        final double[] strikes = { 0.01, 0.02, 0.03, 0.04, 0.05,
                                   0.06, 0.07, 0.08, 0.09 };
        final CapFloor.Type[] types = { CapFloor.Type.Cap, CapFloor.Type.Floor };

        // See testStrikeDependency for the reference-date shift rationale.
        final Date startDate = vars.termStructure.currentLink()
                .referenceDate().add(new Period(vars.frequency));
        final double shift = 1.0e-8;
        final double tolerance = 0.005;

        for (final int length : lengths) {
            for (final double vol : vols) {
                for (final double strike : strikes) {
                    for (final CapFloor.Type type : types) {
                        final Leg leg = vars.makeLeg(startDate, length);

                        // Build the reference cap/floor with its own engine
                        // so we can read the analytic vega via the engine's
                        // results map (C++ uses Instrument::result<T>; Java's
                        // CapFloor.result(key) accessor is deferred to a
                        // follow-up — see Phase 5e.5b-CFC-d-49 commit).
                        final List<Double> strikeList = new ArrayList<Double>(
                                Arrays.asList(Double.valueOf(strike)));
                        final CapFloor capFloor = new CapFloor(type, leg,
                                strikeList, vars.termStructure, null);
                        final BlackCapFloorEngine engine = vars.makeEngine(vol);
                        capFloor.setPricingEngine(engine);

                        // Two bumped cap/floors for FD vega.
                        final CapFloor shifted2 = vars.makeCapFloor(
                                type, leg, strike, vol + shift);
                        final CapFloor shifted1 = vars.makeCapFloor(
                                type, leg, strike, vol - shift);
                        final double value1 = shifted1.NPV();
                        final double value2 = shifted2.NPV();
                        final double numericalVega = (value2 - value1) / (2.0 * shift);

                        if (numericalVega > 1.0e-4) {
                            // Trigger NPV() so the engine populates results.
                            capFloor.NPV();
                            final CapFloor.ResultsImpl results =
                                    (CapFloor.ResultsImpl) engine.getResults();
                            final Object vegaObj = results.additionalResults().get("vega");
                            if (vegaObj == null) {
                                fail("BlackCapFloorEngine did not populate "
                                        + "the 'vega' additional result");
                            }
                            final double analyticalVega = ((Double) vegaObj).doubleValue();
                            double discrepancy = Math.abs(numericalVega - analyticalVega);
                            discrepancy /= numericalVega;
                            if (discrepancy > tolerance) {
                                fail("failed to compute cap/floor vega:"
                                        + "\n   length:      " + length + "Y"
                                        + "\n   strike:      " + strike
                                        + "\n   type:        " + type
                                        + "\n   calculated:  " + analyticalVega
                                        + "\n   expected:    " + numericalVega
                                        + "\n   discrepancy: " + discrepancy
                                        + "\n   tolerance:   " + tolerance);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testStrikeDependency() {
        QL.info("Testing cap/floor dependency on strike...");

        final CommonVars vars = new CommonVars();

        final int[] lengths = { 1, 2, 3, 5, 7, 10, 15, 20 };
        final double[] vols = { 0.01, 0.05, 0.10, 0.15, 0.20 };
        final double[] strikes = { 0.03, 0.04, 0.05, 0.06, 0.07 };

        // Java tweak: start the schedule one period after the curve
        // reference date so the first IborCoupon fixing date is strictly
        // in the future (cf. AnalyticCapFloorEngineTest). Java's
        // IborIndex.fixing path NPEs on missing-but-required-on-eval
        // fixings; C++ falls through to forecast.
        final Date startDate = vars.termStructure.currentLink()
                .referenceDate().add(new Period(vars.frequency));

        for (final int length : lengths) {
            for (final double vol : vols) {
                final double[] capValues = new double[strikes.length];
                final double[] floorValues = new double[strikes.length];
                for (int s = 0; s < strikes.length; ++s) {
                    final double strike = strikes[s];
                    final Leg leg = vars.makeLeg(startDate, length);
                    final CapFloor cap = vars.makeCapFloor(
                            CapFloor.Type.Cap, leg, strike, vol);
                    capValues[s] = cap.NPV();
                    final CapFloor floor = vars.makeCapFloor(
                            CapFloor.Type.Floor, leg, strike, vol);
                    floorValues[s] = floor.NPV();
                }
                // Cap NPV must be non-increasing in strike.
                for (int s = 0; s + 1 < strikes.length; ++s) {
                    if (capValues[s] < capValues[s + 1]) {
                        fail("NPV is increasing with the strike in a cap:\n"
                                + "    length:     " + length + " years\n"
                                + "    volatility: " + vol + "\n"
                                + "    value:      " + capValues[s]
                                + " at strike: " + strikes[s] + "\n"
                                + "    value:      " + capValues[s + 1]
                                + " at strike: " + strikes[s + 1]);
                    }
                }
                // Floor NPV must be non-decreasing in strike.
                for (int s = 0; s + 1 < strikes.length; ++s) {
                    if (floorValues[s] > floorValues[s + 1]) {
                        fail("NPV is decreasing with the strike in a floor:\n"
                                + "    length:     " + length + " years\n"
                                + "    volatility: " + vol + "\n"
                                + "    value:      " + floorValues[s]
                                + " at strike: " + strikes[s] + "\n"
                                + "    value:      " + floorValues[s + 1]
                                + " at strike: " + strikes[s + 1]);
                    }
                }
            }
        }
    }

    @Test
    public void testConsistency() {
        QL.info("Testing consistency between cap, floor and collar...");

        final CommonVars vars = new CommonVars();

        final int[] lengths = { 1, 2, 3, 5, 7, 10, 15, 20 };
        final double[] capRates = { 0.03, 0.04, 0.05, 0.06, 0.07 };
        final double[] floorRates = { 0.03, 0.04, 0.05, 0.06, 0.07 };
        final double[] vols = { 0.01, 0.05, 0.10, 0.15, 0.20 };

        // See testStrikeDependency for the reference-date shift rationale.
        final Date startDate = vars.termStructure.currentLink()
                .referenceDate().add(new Period(vars.frequency));

        for (final int length : lengths) {
            for (final double capRate : capRates) {
                for (final double floorRate : floorRates) {
                    for (final double vol : vols) {
                        final Leg leg = vars.makeLeg(startDate, length);
                        final CapFloor cap = vars.makeCapFloor(
                                CapFloor.Type.Cap, leg, capRate, vol);
                        final CapFloor floor = vars.makeCapFloor(
                                CapFloor.Type.Floor, leg, floorRate, vol);

                        // Build Collar via the explicit cap+floor strikes ctor.
                        final List<Double> capStrikes = new ArrayList<Double>(
                                Arrays.asList(Double.valueOf(capRate)));
                        final List<Double> floorStrikes = new ArrayList<Double>(
                                Arrays.asList(Double.valueOf(floorRate)));
                        final CapFloor collar = new CapFloor(
                                CapFloor.Type.Collar, leg,
                                capStrikes, floorStrikes,
                                vars.termStructure, null);
                        collar.setPricingEngine(vars.makeEngine(vol));

                        final double diff = Math.abs(
                                (cap.NPV() - floor.NPV()) - collar.NPV());
                        if (diff > 1e-10) {
                            fail("inconsistency between cap, floor and collar:\n"
                                    + "    length:       " + length + " years\n"
                                    + "    volatility:   " + vol + "\n"
                                    + "    cap value:    " + cap.NPV()
                                    + " at strike: " + capRate + "\n"
                                    + "    floor value:  " + floor.NPV()
                                    + " at strike: " + floorRate + "\n"
                                    + "    collar value: " + collar.NPV()
                                    + "\n    diff:         " + diff);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testParity() {
        QL.info("Testing cap/floor parity...");

        final CommonVars vars = new CommonVars();

        final int[] lengths = { 1, 2, 3, 5, 7, 10, 15, 20 };
        final double[] strikes = { 0., 0.03, 0.04, 0.05, 0.06, 0.07 };
        final double[] vols = { 0.01, 0.05, 0.10, 0.15, 0.20 };

        // See testStrikeDependency for the reference-date shift rationale.
        final Date startDate = vars.termStructure.currentLink()
                .referenceDate().add(new Period(vars.frequency));

        for (final int length : lengths) {
            for (final double strike : strikes) {
                for (final double vol : vols) {
                    final Leg leg = vars.makeLeg(startDate, length);
                    final CapFloor cap = vars.makeCapFloor(
                            CapFloor.Type.Cap, leg, strike, vol);
                    final CapFloor floor = vars.makeCapFloor(
                            CapFloor.Type.Floor, leg, strike, vol);
                    final Date maturity = vars.calendar.advance(startDate,
                            new Period(length, TimeUnit.Years), vars.convention);
                    final Schedule schedule = new Schedule(startDate, maturity,
                            new Period(vars.frequency), vars.calendar,
                            vars.convention, vars.convention,
                            DateGeneration.Rule.Forward, false);
                    final VanillaSwap swap = new VanillaSwap(
                            VanillaSwap.Type.Payer, vars.nominals[0],
                            schedule, strike, vars.index.dayCounter(),
                            schedule, vars.index, 0.0,
                            vars.index.dayCounter());
                    swap.setPricingEngine(
                            new DiscountingSwapEngine(vars.termStructure));
                    final double diff = Math.abs(
                            (cap.NPV() - floor.NPV()) - swap.NPV());
                    if (diff > 1e-10) {
                        fail("put/call parity violated:\n"
                                + "    length:      " + length + " years\n"
                                + "    volatility:  " + vol + "\n"
                                + "    strike:      " + strike + "\n"
                                + "    cap value:   " + cap.NPV() + "\n"
                                + "    floor value: " + floor.NPV() + "\n"
                                + "    swap value:  " + swap.NPV()
                                + "\n    diff:        " + diff);
                    }
                }
            }
        }
    }

    @Test
    public void testATMRate() {
        QL.info("Testing cap/floor ATM rate...");

        final CommonVars vars = new CommonVars();

        final int[] lengths = { 1, 2, 3, 5, 7, 10, 15, 20 };
        final double[] strikes = { 0., 0.03, 0.04, 0.05, 0.06, 0.07 };
        final double[] vols = { 0.01, 0.05, 0.10, 0.15, 0.20 };

        // See testStrikeDependency for the reference-date shift rationale.
        final Date startDate = vars.termStructure.currentLink()
                .referenceDate().add(new Period(vars.frequency));

        for (final int length : lengths) {
            final Leg leg = vars.makeLeg(startDate, length);
            final Date maturity = vars.calendar.advance(startDate,
                    new Period(length, TimeUnit.Years), vars.convention);
            final Schedule schedule = new Schedule(startDate, maturity,
                    new Period(vars.frequency), vars.calendar,
                    vars.convention, vars.convention,
                    DateGeneration.Rule.Forward, false);

            for (final double strike : strikes) {
                for (final double vol : vols) {
                    final CapFloor cap = vars.makeCapFloor(
                            CapFloor.Type.Cap, leg, strike, vol);
                    final CapFloor floor = vars.makeCapFloor(
                            CapFloor.Type.Floor, leg, strike, vol);
                    final double capATMRate = cap.atmRate();
                    final double floorATMRate = floor.atmRate();
                    if (Math.abs(floorATMRate - capATMRate) >= 1e-10) {
                        fail("Cap ATM Rate and floor ATM Rate should be equal:\n"
                                + "   length:        " + length + " years\n"
                                + "   volatility:    " + vol + "\n"
                                + "   strike:        " + strike + "\n"
                                + "   cap ATM rate:  " + capATMRate + "\n"
                                + "   floor ATM rate:" + floorATMRate);
                    }
                    final VanillaSwap swap = new VanillaSwap(
                            VanillaSwap.Type.Payer, vars.nominals[0],
                            schedule, floorATMRate, vars.index.dayCounter(),
                            schedule, vars.index, 0.0,
                            vars.index.dayCounter());
                    swap.setPricingEngine(
                            new DiscountingSwapEngine(vars.termStructure));
                    final double swapNPV = swap.NPV();
                    if (Math.abs(swapNPV) >= 1e-10) {
                        fail("the NPV of a Swap struck at ATM rate "
                                + "should be equal to 0:\n"
                                + "   length:        " + length + " years\n"
                                + "   volatility:    " + vol + "\n"
                                + "   ATM rate:      " + floorATMRate + "\n"
                                + "   swap NPV:      " + swapNPV);
                    }
                }
            }
        }
    }

    @Ignore("Phase 5e.5 WI-5e.5-CF-1: MakeCapFloor now ported (commit c1e9cb84); "
            + "needs implied-volatility solver wiring on CapFloor.")
    @Test
    public void testImpliedVolatility() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-CF-1: MakeCapFloor now ported (commit c1e9cb84); "
            + "needs cached NPVs regenerated from C++ v1.42.1 (probe candidate).")
    @Test
    public void testCachedValue() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CF-3 — needs CapFloor.optionletsPrice()/"
            + "optionletsBPS() result-map accessors.")
    @Test
    public void testCachedValueFromOptionLets() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CF-3 — needs CapFloor.optionletsDelta() accessor "
            + "+ Black-mode capfloor engine delta computation.")
    @Test
    public void testOptionLetsDelta() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CF-2 + WI-5e.5-CF-3 — needs Bachelier-mode "
            + "CapFloor engine + optionletsDelta accessor.")
    @Test
    public void testBachelierOptionLetsDelta() {
    }
}
