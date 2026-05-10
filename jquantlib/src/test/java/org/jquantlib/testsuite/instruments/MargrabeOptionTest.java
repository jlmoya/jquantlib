/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.MargrabeOption;
import org.jquantlib.pricingengines.exchange.AnalyticAmericanMargrabeEngine;
import org.jquantlib.pricingengines.exchange.AnalyticEuropeanMargrabeEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase Body-Fill-5 port of {@code test-suite/margrabeoption.cpp} v1.42.1
 * (553 LOC, 3 cases).
 *
 * <p>Exercises the Margrabe two-asset exchange option (option to exchange
 * one risky asset for another), in both European and American flavours.
 *
 * <p><strong>Body-fills (Phase Body-Fill-5):</strong>
 * <ul>
 *   <li>{@link #testEuroExchangeTwoAssets()} — 21 reference cases from C++
 *       (article p.52 + Excel quantity tests), tolerance 1e-3.
 *   <li>{@link #testAmericanExchangeTwoAssets()} — 4 reference cases from
 *       Bjerksund-Stensland 1993 worked example, tolerance 1.0e-2.
 * </ul>
 *
 * <p><strong>Carry-forward to Phase 5i.5</strong>:
 * <ul>
 *   <li>{@code testGreeks} — bulky perturbation matrix for two-asset Greeks
 *       (delta1, delta2, gamma1, gamma2, theta, rho). Tractable but ~250 LOC.
 * </ul>
 *
 * <p>Source: {@code test-suite/margrabeoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class MargrabeOptionTest {

    public MargrabeOptionTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private static final String REASON_GREEKS =
            "Phase 5i.5: MargrabeOption ported; numerical-derivative Greeks harness for "
          + "two-asset payoffs needs body fill from C++ margrabeoption.cpp::testGreeks "
          + "(bulky perturbation matrix ~250 LOC; deferred).";

    /** C++ helper {@code timeToDays(Time t, Integer daysPerYear=360)}. */
    private static int timeToDays(final double t) {
        return (int) (t * 360 + 0.5);
    }

    /** Single C++ {@code MargrabeOptionTwoData} row. */
    private static final class MargrabeOptionTwoData {
        final double s1, s2;
        final int Q1, Q2;
        final double q1, q2, r, t;
        final double v1, v2, rho;
        final double result;
        final double delta1, delta2;
        final double gamma1, gamma2;
        final double theta, rho_greek;
        final double tol;

        MargrabeOptionTwoData(final double s1, final double s2,
                              final int Q1, final int Q2,
                              final double q1, final double q2, final double r,
                              final double t,
                              final double v1, final double v2, final double rho,
                              final double result,
                              final double delta1, final double delta2,
                              final double gamma1, final double gamma2,
                              final double theta, final double rho_greek,
                              final double tol) {
            this.s1 = s1; this.s2 = s2;
            this.Q1 = Q1; this.Q2 = Q2;
            this.q1 = q1; this.q2 = q2; this.r = r;
            this.t = t;
            this.v1 = v1; this.v2 = v2; this.rho = rho;
            this.result = result;
            this.delta1 = delta1; this.delta2 = delta2;
            this.gamma1 = gamma1; this.gamma2 = gamma2;
            this.theta = theta; this.rho_greek = rho_greek;
            this.tol = tol;
        }
    }

    /** Single C++ {@code MargrabeAmericanOptionTwoData} row. */
    private static final class MargrabeAmericanOptionTwoData {
        final double s1, s2;
        final int Q1, Q2;
        final double q1, q2, r, t;
        final double v1, v2, rho;
        final double result;
        final double tol;

        MargrabeAmericanOptionTwoData(final double s1, final double s2,
                                       final int Q1, final int Q2,
                                       final double q1, final double q2, final double r,
                                       final double t,
                                       final double v1, final double v2, final double rho,
                                       final double result, final double tol) {
            this.s1 = s1; this.s2 = s2;
            this.Q1 = Q1; this.Q2 = Q2;
            this.q1 = q1; this.q2 = q2; this.r = r;
            this.t = t;
            this.v1 = v1; this.v2 = v2; this.rho = rho;
            this.result = result; this.tol = tol;
        }
    }

    /**
     * Port of C++ {@code margrabeoption.cpp::testEuroExchangeTwoAssets}.
     *
     * <p>Exchange-one-asset-for-another European options.  Reference data
     * from the article cited in the C++ source (p.52) + Excel quantity
     * tests. Tolerance 1e-3 (tier-stratified LOOSE for analytic).
     */
    @Test
    public void testEuroExchangeTwoAssets() {
        final MargrabeOptionTwoData[] values = new MargrabeOptionTwoData[] {
            // s1, s2, Q1, Q2, q1, q2, r, t, v1, v2, rho, result, delta1, delta2, gamma1, gamma2, theta, rho_greek, tol
            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.15, -0.50, 2.125, 0.841, -0.818, 0.112, 0.135, -2.043, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.20, -0.50, 2.199, 0.813, -0.784, 0.109, 0.132, -2.723, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.25, -0.50, 2.283, 0.788, -0.753, 0.105, 0.126, -3.419, 0.0, 1.0e-3),

            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.15, 0.00, 2.045, 0.883, -0.870, 0.108, 0.131, -1.168, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.20, 0.00, 2.091, 0.857, -0.838, 0.112, 0.135, -1.698, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.25, 0.00, 2.152, 0.830, -0.805, 0.111, 0.134, -2.302, 0.0, 1.0e-3),

            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.15, 0.50, 1.974, 0.946, -0.942, 0.079, 0.096, -0.126, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.20, 0.50, 1.989, 0.929, -0.922, 0.092, 0.111, -0.398, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.25, 0.50, 2.019, 0.902, -0.891, 0.104, 0.125, -0.838, 0.0, 1.0e-3),

            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.15, -0.50, 2.762, 0.672, -0.602, 0.072, 0.087, -1.207, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.20, -0.50, 2.989, 0.661, -0.578, 0.064, 0.078, -1.457, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.25, -0.50, 3.228, 0.653, -0.557, 0.058, 0.070, -1.712, 0.0, 1.0e-3),

            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.15, 0.00, 2.479, 0.695, -0.640, 0.085, 0.102, -0.874, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.20, 0.00, 2.650, 0.680, -0.616, 0.077, 0.093, -1.078, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.25, 0.00, 2.847, 0.668, -0.592, 0.069, 0.083, -1.302, 0.0, 1.0e-3),

            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.15, 0.50, 2.138, 0.746, -0.713, 0.106, 0.128, -0.416, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.20, 0.50, 2.231, 0.728, -0.689, 0.099, 0.120, -0.550, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.25, 0.50, 2.374, 0.707, -0.659, 0.090, 0.109, -0.741, 0.0, 1.0e-3),

            // Quantity tests from Excel
            new MargrabeOptionTwoData(22.0, 10.0, 1, 2, 0.06, 0.04, 0.10, 0.50, 0.20, 0.15, 0.50, 2.138, 0.746, -1.426, 0.106, 0.255, -0.987, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(11.0, 20.0, 2, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.20, 0.50, 2.231, 1.455, -0.689, 0.198, 0.120, 0.410, 0.0, 1.0e-3),
            new MargrabeOptionTwoData(11.0, 10.0, 2, 2, 0.06, 0.04, 0.10, 0.50, 0.20, 0.25, 0.50, 2.374, 1.413, -1.317, 0.181, 0.219, -0.336, 0.0, 1.0e-3)
        };

        final DayCounter dc = new Actual360();
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot1 = new SimpleQuote(0.0);
        final SimpleQuote spot2 = new SimpleQuote(0.0);
        final SimpleQuote qRate1 = new SimpleQuote(0.0);
        final SimpleQuote qRate2 = new SimpleQuote(0.0);
        final SimpleQuote rRate  = new SimpleQuote(0.0);
        final SimpleQuote vol1   = new SimpleQuote(0.0);
        final SimpleQuote vol2   = new SimpleQuote(0.0);

        final Handle<? extends Quote> spot1H = new Handle<SimpleQuote>(spot1);
        final Handle<? extends Quote> spot2H = new Handle<SimpleQuote>(spot2);
        final Calendar cal = new NullCalendar();
        final Handle<YieldTermStructure> qTS1 = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(qRate1), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> qTS2 = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(qRate2), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(rRate), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<BlackVolTermStructure> volTS1 = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, new Handle<Quote>(vol1), dc));
        final Handle<BlackVolTermStructure> volTS2 = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, new Handle<Quote>(vol2), dc));

        final GeneralizedBlackScholesProcess proc1 = new GeneralizedBlackScholesProcess(
                spot1H, qTS1, rTS, volTS1);
        final GeneralizedBlackScholesProcess proc2 = new GeneralizedBlackScholesProcess(
                spot2H, qTS2, rTS, volTS2);

        for (final MargrabeOptionTwoData v : values) {
            final Date exDate = today.add(timeToDays(v.t));
            final Exercise exercise = new EuropeanExercise(exDate);

            spot1.setValue(v.s1);
            spot2.setValue(v.s2);
            qRate1.setValue(v.q1);
            qRate2.setValue(v.q2);
            rRate.setValue(v.r);
            vol1.setValue(v.v1);
            vol2.setValue(v.v2);

            final AnalyticEuropeanMargrabeEngine engine =
                    new AnalyticEuropeanMargrabeEngine(proc1, proc2, v.rho);
            final MargrabeOption option = new MargrabeOption(v.Q1, v.Q2, exercise);
            option.setPricingEngine(engine);

            // Value
            final double calculated = option.NPV();
            final double error = Math.abs(calculated - v.result);
            if (error > v.tol) {
                fail("Margrabe value: expected=" + v.result
                        + " calculated=" + calculated
                        + " error=" + error + " tolerance=" + v.tol
                        + "\n    s1=" + v.s1 + " s2=" + v.s2 + " Q1=" + v.Q1 + " Q2=" + v.Q2
                        + " q1=" + v.q1 + " q2=" + v.q2 + " r=" + v.r
                        + " t=" + v.t + " v1=" + v.v1 + " v2=" + v.v2 + " rho=" + v.rho);
            }

            // Greeks
            checkGreek("delta1", v, v.delta1, option.delta1());
            checkGreek("delta2", v, v.delta2, option.delta2());
            checkGreek("gamma1", v, v.gamma1, option.gamma1());
            checkGreek("gamma2", v, v.gamma2, option.gamma2());
            checkGreek("theta",  v, v.theta,  option.theta());
            checkGreek("rho",    v, v.rho_greek, option.rho());
        }
        assertTrue("testEuroExchangeTwoAssets passed " + values.length + " cases", true);
    }

    private static void checkGreek(final String name, final MargrabeOptionTwoData v,
                                    final double expected, final double calculated) {
        final double error = Math.abs(calculated - expected);
        if (error > v.tol) {
            fail("Margrabe " + name + ": expected=" + expected
                    + " calculated=" + calculated
                    + " error=" + error + " tolerance=" + v.tol
                    + "\n    s1=" + v.s1 + " s2=" + v.s2 + " Q1=" + v.Q1 + " Q2=" + v.Q2
                    + " q1=" + v.q1 + " q2=" + v.q2 + " r=" + v.r
                    + " t=" + v.t + " v1=" + v.v1 + " v2=" + v.v2 + " rho=" + v.rho);
        }
    }

    /**
     * Port of C++ {@code margrabeoption.cpp::testAmericanExchangeTwoAssets}.
     *
     * <p>18 reference cases from Haug, tolerance 1.0e-3 — exercises the
     * Bjerksund-Stensland 1993 American-exchange approximation.
     */
    @Test
    public void testAmericanExchangeTwoAssets() {
        final MargrabeAmericanOptionTwoData[] values = new MargrabeAmericanOptionTwoData[] {
            //s1,  s2,   Q1, Q2, q1,  q2,  r,    t,    v1,   v2,   rho,  result, tol
            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.15, -0.50, 2.1357, 1.0e-3),
            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.20, -0.50, 2.2074, 1.0e-3),
            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.25, -0.50, 2.2902, 1.0e-3),

            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.15,  0.00, 2.0592, 1.0e-3),
            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.20,  0.00, 2.1032, 1.0e-3),
            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.25,  0.00, 2.1618, 1.0e-3),

            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.15,  0.50, 2.0001, 1.0e-3),
            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.20,  0.50, 2.0110, 1.0e-3),
            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.10, 0.20, 0.25,  0.50, 2.0359, 1.0e-3),

            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.15, -0.50, 2.8051, 1.0e-3),
            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.20, -0.50, 3.0288, 1.0e-3),
            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.25, -0.50, 3.2664, 1.0e-3),

            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.15,  0.00, 2.5282, 1.0e-3),
            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.20,  0.00, 2.6945, 1.0e-3),
            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.25,  0.00, 2.8893, 1.0e-3),

            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.15,  0.50, 2.2053, 1.0e-3),
            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.20,  0.50, 2.2906, 1.0e-3),
            new MargrabeAmericanOptionTwoData(22.0, 20.0, 1, 1, 0.06, 0.04, 0.10, 0.50, 0.20, 0.25,  0.50, 2.4261, 1.0e-3)
        };

        final DayCounter dc = new Actual360();
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot1 = new SimpleQuote(0.0);
        final SimpleQuote spot2 = new SimpleQuote(0.0);
        final SimpleQuote qRate1 = new SimpleQuote(0.0);
        final SimpleQuote qRate2 = new SimpleQuote(0.0);
        final SimpleQuote rRate  = new SimpleQuote(0.0);
        final SimpleQuote vol1   = new SimpleQuote(0.0);
        final SimpleQuote vol2   = new SimpleQuote(0.0);

        final Handle<? extends Quote> spot1H = new Handle<SimpleQuote>(spot1);
        final Handle<? extends Quote> spot2H = new Handle<SimpleQuote>(spot2);
        final Calendar cal = new NullCalendar();
        final Handle<YieldTermStructure> qTS1 = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(qRate1), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> qTS2 = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(qRate2), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(rRate), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<BlackVolTermStructure> volTS1 = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, new Handle<Quote>(vol1), dc));
        final Handle<BlackVolTermStructure> volTS2 = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, new Handle<Quote>(vol2), dc));

        final GeneralizedBlackScholesProcess proc1 = new GeneralizedBlackScholesProcess(
                spot1H, qTS1, rTS, volTS1);
        final GeneralizedBlackScholesProcess proc2 = new GeneralizedBlackScholesProcess(
                spot2H, qTS2, rTS, volTS2);

        for (final MargrabeAmericanOptionTwoData v : values) {
            final Date exDate = today.add(timeToDays(v.t));
            final Exercise exercise = new AmericanExercise(today, exDate);

            spot1.setValue(v.s1);
            spot2.setValue(v.s2);
            qRate1.setValue(v.q1);
            qRate2.setValue(v.q2);
            rRate.setValue(v.r);
            vol1.setValue(v.v1);
            vol2.setValue(v.v2);

            final AnalyticAmericanMargrabeEngine engine =
                    new AnalyticAmericanMargrabeEngine(proc1, proc2, v.rho);
            final MargrabeOption option = new MargrabeOption(v.Q1, v.Q2, exercise);
            option.setPricingEngine(engine);

            final double calculated = option.NPV();
            final double error = Math.abs(calculated - v.result);
            if (error > v.tol) {
                fail("American Margrabe value: expected=" + v.result
                        + " calculated=" + calculated
                        + " error=" + error + " tolerance=" + v.tol
                        + "\n    s1=" + v.s1 + " s2=" + v.s2 + " Q1=" + v.Q1 + " Q2=" + v.Q2
                        + " q1=" + v.q1 + " q2=" + v.q2 + " r=" + v.r
                        + " t=" + v.t + " v1=" + v.v1 + " v2=" + v.v2 + " rho=" + v.rho);
            }
        }
        assertTrue("testAmericanExchangeTwoAssets passed " + values.length + " cases", true);
    }

    @Ignore(REASON_GREEKS)
    @Test
    public void testGreeks() { fail("not implemented"); }
}
