/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.lookback;

import static org.junit.Assert.assertEquals;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.ContinuousPartialFloatingLookbackOption;
import org.jquantlib.instruments.FloatingTypePayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.TypePayoff;
import org.jquantlib.pricingengines.lookback.AnalyticContinuousPartialFloatingLookbackEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validated tests for
 * {@link AnalyticContinuousPartialFloatingLookbackEngine} against v1.42.1
 * reference values in
 * {@code migration-harness/references/lookback/analytic_continuous_partial_floating_lookback.json}.
 *
 * <p>Tolerance tier: TIGHT — abs 1e-9 (Heynen-Kat partial-time formula;
 * uses {@code BivariateNormalDistribution} (West/Genz 2004) under the
 * hood — same algorithm as the C++ {@code BivariateCumulativeNormal*We04DP},
 * so the quadrature lines up bit-for-bit).
 */
public class AnalyticContinuousPartialFloatingLookbackEngineTest {

    private static final String GROUP = "lookback/analytic_continuous_partial_floating_lookback";
    private static final ReferenceReader REF = ReferenceReader.load(GROUP);

    private static final double ABS_TOL = 1e-9;

    private static GeneralizedBlackScholesProcess makeProcess(
            final Date today, final double S, final double r, final double q, final double vol,
            final DayCounter dc) {
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(S));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(r)), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(q)), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, new NullCalendar(),
                        new Handle<Quote>(new SimpleQuote(vol)), dc));
        return new BlackScholesMertonProcess(spot, qTS, rTS, volTS);
    }

    private void runCase(final String caseName) {
        final Case c = REF.getCase(caseName);
        final JSONObject in = c.inputs();
        final JSONObject e = (JSONObject) c.expectedRaw();

        final double S = in.getDouble("S");
        final double r = in.getDouble("r");
        final double q = in.getDouble("q");
        final double vol = in.getDouble("vol");
        final double minmax = in.getDouble("minmax");
        final double lambda = in.getDouble("lambda");
        final double lookbackEndYears = in.getDouble("lookback_end_years");
        final double maturityYears = in.getDouble("maturity_years");
        final String typeStr = in.getString("option_type");
        final Option.Type type = "Call".equals(typeStr) ? Option.Type.Call : Option.Type.Put;
        final double expectedNpv = e.getDouble("npv");

        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();

        final GeneralizedBlackScholesProcess process = makeProcess(today, S, r, q, vol, dc);

        final Date maturity = today.add((int) Math.round(maturityYears * 365));
        final Date lookbackEnd = today.add((int) Math.round(lookbackEndYears * 365));
        final Exercise exercise = new EuropeanExercise(maturity);
        final TypePayoff payoff = new FloatingTypePayoff(type);

        final ContinuousPartialFloatingLookbackOption option =
                new ContinuousPartialFloatingLookbackOption(minmax, lambda, lookbackEnd, payoff, exercise);
        option.setPricingEngine(new AnalyticContinuousPartialFloatingLookbackEngine(process));

        final double npv = option.NPV();
        assertEquals("npv mismatch for " + caseName,
                expectedNpv, npv, ABS_TOL);
    }

    @Test public void callPartial_l1_min100_e05_T1_v20()    { runCase("call_partial_l1_min100_e0.5_T1_v20"); }
    @Test public void callPartial_l12_min100_e05_T1_v20()   { runCase("call_partial_l1.2_min100_e0.5_T1_v20"); }
    @Test public void callPartial_l15_min90_e07_T1_v25()    { runCase("call_partial_l1.5_min90_e0.7_T1_v25"); }
    @Test public void callFull_l1_min100_T1_v20()           { runCase("call_full_l1_min100_T1_v20"); }
    @Test public void callFull_l12_min100_T1_v20()          { runCase("call_full_l1.2_min100_T1_v20"); }
    @Test public void putPartial_l1_max100_e05_T1_v20()     { runCase("put_partial_l1_max100_e0.5_T1_v20"); }
    @Test public void putPartial_l08_max100_e05_T1_v20()    { runCase("put_partial_l0.8_max100_e0.5_T1_v20"); }
    @Test public void putPartial_l07_max110_e07_T1_v25()    { runCase("put_partial_l0.7_max110_e0.7_T1_v25"); }
    @Test public void putFull_l1_max100_T1_v20()            { runCase("put_full_l1_max100_T1_v20"); }
    @Test public void putFull_l09_max100_T1_v20()           { runCase("put_full_l0.9_max100_T1_v20"); }
}
