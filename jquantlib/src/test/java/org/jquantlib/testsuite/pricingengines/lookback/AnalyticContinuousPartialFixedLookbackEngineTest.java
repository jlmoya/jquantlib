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
import org.jquantlib.instruments.ContinuousPartialFixedLookbackOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.lookback.AnalyticContinuousPartialFixedLookbackEngine;
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
 * {@link AnalyticContinuousPartialFixedLookbackEngine} against v1.42.1
 * reference values in
 * {@code migration-harness/references/lookback/analytic_continuous_partial_fixed_lookback.json}.
 *
 * <p>Tolerance tier: TIGHT — abs 1e-9 (Heynen-Kat partial-time formula;
 * uses {@code BivariateNormalDistribution} (West/Genz 2004) under the
 * hood — same algorithm as the C++ {@code BivariateCumulativeNormal*We04DP},
 * so the quadrature lines up bit-for-bit).
 */
public class AnalyticContinuousPartialFixedLookbackEngineTest {

    private static final String GROUP = "lookback/analytic_continuous_partial_fixed_lookback";
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
        final double strike = in.getDouble("strike");
        final double lookbackStartYears = in.getDouble("lookback_start_years");
        final double maturityYears = in.getDouble("maturity_years");
        final String typeStr = in.getString("option_type");
        final Option.Type type = "Call".equals(typeStr) ? Option.Type.Call : Option.Type.Put;
        final double expectedNpv = e.getDouble("npv");

        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();

        final GeneralizedBlackScholesProcess process = makeProcess(today, S, r, q, vol, dc);

        final Date maturity = today.add((int) Math.round(maturityYears * 365));
        final Date lookbackStart = today.add((int) Math.round(lookbackStartYears * 365));
        final Exercise exercise = new EuropeanExercise(maturity);
        final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);

        final ContinuousPartialFixedLookbackOption option =
                new ContinuousPartialFixedLookbackOption(lookbackStart, payoff, exercise);
        option.setPricingEngine(new AnalyticContinuousPartialFixedLookbackEngine(process));

        final double npv = option.NPV();
        assertEquals("npv mismatch for " + caseName,
                expectedNpv, npv, ABS_TOL);
    }

    @Test public void callStrike90_lstart03_T1_v20()      { runCase("call_strike90_lstart0.3_T1_v20"); }
    @Test public void callStrike100_lstart03_T1_v20()     { runCase("call_strike100_lstart0.3_T1_v20"); }
    @Test public void callStrike110_lstart05_T1_v25()     { runCase("call_strike110_lstart0.5_T1_v25"); }
    @Test public void callStrike90_lstart10_T2_v30()      { runCase("call_strike90_lstart1.0_T2_v30"); }
    @Test public void callStrike100_lstartEqT1_v20()      { runCase("call_strike100_lstart_eq_T1_v20"); }
    @Test public void putStrike110_lstart03_T1_v20()      { runCase("put_strike110_lstart0.3_T1_v20"); }
    @Test public void putStrike100_lstart03_T1_v20()      { runCase("put_strike100_lstart0.3_T1_v20"); }
    @Test public void putStrike90_lstart05_T1_v25()       { runCase("put_strike90_lstart0.5_T1_v25"); }
    @Test public void putStrike110_lstart10_T2_v30()      { runCase("put_strike110_lstart1.0_T2_v30"); }
    @Test public void putStrike100_lstartEqT1_v20()       { runCase("put_strike100_lstart_eq_T1_v20"); }
}
