/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.QuantoForwardVanillaOption;
import org.jquantlib.instruments.QuantoVanillaOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.quanto.QuantoForwardVanillaEngine;
import org.jquantlib.pricingengines.quanto.QuantoVanillaEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5i skeleton port of {@code test-suite/quantooption.cpp} v1.42.1
 * (1,345 LOC, 10 cases).
 *
 * <p>Exercises quanto-adjusted vanilla, forward, barrier, and double-barrier
 * options under both analytic and FD engines, plus the {@code FdmQuantoHelper}
 * utility and the American quanto path.
 *
 * <p><strong>All 10 cases deferred to Phase 5i.5</strong> — Java has no
 * equivalent for the quanto vanilla / forward families:
 * <ul>
 *   <li>No {@code QuantoVanillaOption} instrument (only
 *       {@code experimental.barrieroption.QuantoDoubleBarrierOption} exists);
 *   <li>No {@code QuantoEngine} / {@code QuantoForwardEngine} /
 *       {@code QuantoBarrierEngine} ports;
 *   <li>No {@code FdmQuantoHelper} port (used for FD quanto adjustments
 *       in the Phase 2m FD vanilla framework);
 *   <li>No FD quanto vanilla engine ({@code FdBlackScholesVanillaEngine}
 *       in Java does not yet expose the quanto-helper hook).
 * </ul>
 *
 * <p>Source: {@code test-suite/quantooption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class QuantoOptionTest {

    private static final String REASON_VANILLA =
            "Phase 5i.5: QuantoVanillaOption + QuantoVanillaEngine now ported "
          + "(see org.jquantlib.instruments.QuantoVanillaOption + "
          + "org.jquantlib.pricingengines.quanto.QuantoVanillaEngine); "
          + "test body is `fail(\"not implemented\")` — needs full port from C++ quantooption.cpp.";

    private static final String REASON_FORWARD =
            "Phase 5i.5: QuantoForwardVanillaOption now ported (Phase 5i.5-MGR commit); "
          + "test body is `fail(\"not implemented\")` — needs full port from C++ quantooption.cpp.";

    private static final String REASON_BARRIER =
            "Phase 5i.5 — requires QuantoBarrierOption + QuantoBarrierEngine "
          + "port (Java has only QuantoDoubleBarrierOption under experimental)";

    private static final String REASON_FDM_HELPER =
            "Phase 5i.5 — requires FdmQuantoHelper port + FD vanilla engine "
          + "quanto-helper hook (Phase 2m FD framework prereq)";

    private static final String REASON_AMERICAN =
            "Phase 5i.5 — requires American FD quanto engine path "
          + "(FdBlackScholesVanillaEngine + quanto helper)";

    private static final String REASON_DOUBLE_BARRIER =
            "Phase 5i.5 — QuantoDoubleBarrierOption exists under experimental; "
          + "in-instruments-package wrapper test deferred until promotion";

    /** C++ test-suite helper {@code timeToDays(Time t, Integer daysPerYear=360)}. */
    private static int timeToDays(final double t) {
        return (int) (t * 360 + 0.5);
    }

    /** Single C++ {@code QuantoOptionData} row. */
    private static final class QuantoOptionData {
        final Option.Type type;
        final double strike;
        final double s;     // spot
        final double q;     // dividend
        final double r;     // domestic rate
        final double t;     // time to maturity
        final double v;     // volatility
        final double fxr;   // foreign risk-free rate
        final double fxv;   // FX volatility
        final double corr;  // correlation
        final double result;
        final double tol;
        QuantoOptionData(final Option.Type type, final double strike,
                         final double s, final double q, final double r, final double t,
                         final double v, final double fxr, final double fxv,
                         final double corr, final double result, final double tol) {
            this.type = type; this.strike = strike;
            this.s = s; this.q = q; this.r = r; this.t = t;
            this.v = v; this.fxr = fxr; this.fxv = fxv;
            this.corr = corr; this.result = result; this.tol = tol;
        }
    }

    @Test
    public void testValues() {
        QL.info("Testing quanto option values...");
        // Java port of v1.42.1 test-suite/quantooption.cpp::testValues.
        // Reference values from "Option pricing formulas", Haug, McGraw-Hill 1998.

        final QuantoOptionData[] values = {
            // type, strike, spot, q, r, t, v, fxr, fxv, corr, expected, tol
            new QuantoOptionData(Option.Type.Call, 105.0, 100.0, 0.04, 0.08, 0.5, 0.2,
                    0.05, 0.10, 0.3, 5.3280 / 1.5, 1.0e-4),
            new QuantoOptionData(Option.Type.Put, 105.0, 100.0, 0.04, 0.08, 0.5, 0.2,
                    0.05, 0.10, 0.3, 8.1636, 1.0e-4)
        };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(today, qRate, dc));
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(today, rRate, dc));
        final SimpleQuote vol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                Utilities.flatVol(today, vol, dc));

        final SimpleQuote fxRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> fxrTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(today, fxRate, dc));
        final SimpleQuote fxVol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> fxVolTS = new Handle<BlackVolTermStructure>(
                Utilities.flatVol(today, fxVol, dc));
        final SimpleQuote correlation = new SimpleQuote(0.0);

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<Quote>(spot), qTS, rTS, volTS);
        final PricingEngine engine = new QuantoVanillaEngine(
                stochProcess, fxrTS, fxVolTS, new Handle<Quote>(correlation));

        for (final QuantoOptionData v : values) {
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(v.type, v.strike);
            final Date exDate = today.add(timeToDays(v.t));
            final Exercise exercise = new EuropeanExercise(exDate);

            spot.setValue(v.s);
            qRate.setValue(v.q);
            rRate.setValue(v.r);
            vol.setValue(v.v);
            fxRate.setValue(v.fxr);
            fxVol.setValue(v.fxv);
            correlation.setValue(v.corr);

            final QuantoVanillaOption option = new QuantoVanillaOption(payoff, exercise);
            option.setPricingEngine(engine);

            final double calculated = option.NPV();
            final double error = Math.abs(calculated - v.result);
            final double tolerance = 1.0e-4;
            if (error > tolerance) {
                fail("failed to reproduce quanto-option value:"
                        + "\n    expected:   " + v.result
                        + "\n    calculated: " + calculated
                        + "\n    error:      " + error
                        + "\n    tolerance:  " + tolerance
                        + "\n    type=" + v.type + " strike=" + v.strike
                        + " s=" + v.s + " corr=" + v.corr);
            }
        }
    }

    @Test
    public void testGreeks() {
        QL.info("Testing quanto option greeks...");
        // Java port of v1.42.1 test-suite/quantooption.cpp::testGreeks.
        // Cross-validates analytic Greeks against numerical bumps for
        // 720 case combinations.

        final java.util.Map<String, Double> calculated = new java.util.HashMap<>();
        final java.util.Map<String, Double> expected = new java.util.HashMap<>();
        final java.util.Map<String, Double> tolerance = new java.util.HashMap<>();
        tolerance.put("delta",   1.0e-5);
        tolerance.put("gamma",   1.0e-5);
        tolerance.put("theta",   1.0e-5);
        tolerance.put("rho",     1.0e-5);
        tolerance.put("divRho",  1.0e-5);
        tolerance.put("vega",    1.0e-5);
        tolerance.put("qrho",    1.0e-5);
        tolerance.put("qvega",   1.0e-5);
        tolerance.put("qlambda", 1.0e-5);

        final Option.Type[] types = { Option.Type.Call, Option.Type.Put };
        final double[] strikes = { 50.0, 99.5, 100.0, 100.5, 150.0 };
        final double[] underlyings = { 100.0 };
        final double[] qRates = { 0.04, 0.05 };
        final double[] rRates = { 0.01, 0.05, 0.15 };
        final int[] lengths = { 2 };
        final double[] vols = { 0.11, 1.20 };
        final double[] correlations = { 0.10, 0.90 };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new org.jquantlib.Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(qRate, dc));
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(rRate, dc));
        final SimpleQuote vol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                Utilities.flatVol(vol, dc));
        final SimpleQuote fxRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> fxrTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(fxRate, dc));
        final SimpleQuote fxVol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> fxVolTS = new Handle<BlackVolTermStructure>(
                Utilities.flatVol(fxVol, dc));
        final SimpleQuote correlation = new SimpleQuote(0.0);

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<Quote>(spot), qTS, rTS, volTS);
        final PricingEngine engine = new QuantoVanillaEngine(
                stochProcess, fxrTS, fxVolTS, new Handle<Quote>(correlation));

        for (final Option.Type type : types) {
            for (final double strike : strikes) {
                for (final int length : lengths) {
                    final Date exDate = today.add(new org.jquantlib.time.Period(
                            length, org.jquantlib.time.TimeUnit.Years));
                    final Exercise exercise = new EuropeanExercise(exDate);
                    final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);

                    final QuantoVanillaOption option = new QuantoVanillaOption(payoff, exercise);
                    option.setPricingEngine(engine);

                    for (final double u : underlyings) {
                        for (final double m : qRates) {
                            for (final double n : rRates) {
                                for (final double v : vols) {
                                    for (final double fxr : rRates) {
                                        for (final double fxv : vols) {
                                            for (final double corr : correlations) {
                                                final double q = m, r = n;
                                                spot.setValue(u);
                                                qRate.setValue(q);
                                                rRate.setValue(r);
                                                vol.setValue(v);
                                                fxRate.setValue(fxr);
                                                fxVol.setValue(fxv);
                                                correlation.setValue(corr);

                                                final double value = option.NPV();
                                                calculated.put("delta",   option.delta());
                                                calculated.put("gamma",   option.gamma());
                                                calculated.put("theta",   option.theta());
                                                calculated.put("rho",     option.rho());
                                                calculated.put("divRho",  option.dividendRho());
                                                calculated.put("vega",    option.vega());
                                                calculated.put("qrho",    option.qrho());
                                                calculated.put("qvega",   option.qvega());
                                                calculated.put("qlambda", option.qlambda());

                                                if (value > spot.value() * 1.0e-5) {
                                                    final double du = u * 1.0e-4;
                                                    spot.setValue(u + du);
                                                    double value_p = option.NPV();
                                                    final double delta_p = option.delta();
                                                    spot.setValue(u - du);
                                                    double value_m = option.NPV();
                                                    final double delta_m = option.delta();
                                                    spot.setValue(u);
                                                    expected.put("delta", (value_p - value_m) / (2 * du));
                                                    expected.put("gamma", (delta_p - delta_m) / (2 * du));

                                                    final double dr = r * 1.0e-4;
                                                    rRate.setValue(r + dr);
                                                    value_p = option.NPV();
                                                    rRate.setValue(r - dr);
                                                    value_m = option.NPV();
                                                    rRate.setValue(r);
                                                    expected.put("rho", (value_p - value_m) / (2 * dr));

                                                    final double dq = q * 1.0e-4;
                                                    qRate.setValue(q + dq);
                                                    value_p = option.NPV();
                                                    qRate.setValue(q - dq);
                                                    value_m = option.NPV();
                                                    qRate.setValue(q);
                                                    expected.put("divRho", (value_p - value_m) / (2 * dq));

                                                    final double dv = v * 1.0e-4;
                                                    vol.setValue(v + dv);
                                                    value_p = option.NPV();
                                                    vol.setValue(v - dv);
                                                    value_m = option.NPV();
                                                    vol.setValue(v);
                                                    expected.put("vega", (value_p - value_m) / (2 * dv));

                                                    final double dfxr = fxr * 1.0e-4;
                                                    fxRate.setValue(fxr + dfxr);
                                                    value_p = option.NPV();
                                                    fxRate.setValue(fxr - dfxr);
                                                    value_m = option.NPV();
                                                    fxRate.setValue(fxr);
                                                    expected.put("qrho", (value_p - value_m) / (2 * dfxr));

                                                    final double dfxv = fxv * 1.0e-4;
                                                    fxVol.setValue(fxv + dfxv);
                                                    value_p = option.NPV();
                                                    fxVol.setValue(fxv - dfxv);
                                                    value_m = option.NPV();
                                                    fxVol.setValue(fxv);
                                                    expected.put("qvega", (value_p - value_m) / (2 * dfxv));

                                                    final double dcorr = corr * 1.0e-4;
                                                    correlation.setValue(corr + dcorr);
                                                    value_p = option.NPV();
                                                    correlation.setValue(corr - dcorr);
                                                    value_m = option.NPV();
                                                    correlation.setValue(corr);
                                                    expected.put("qlambda", (value_p - value_m) / (2 * dcorr));

                                                    // theta: perturb evaluation date by +-1 day
                                                    final double dT = dc.yearFraction(today.sub(1), today.add(1));
                                                    new org.jquantlib.Settings().setEvaluationDate(today.sub(1));
                                                    value_m = option.NPV();
                                                    new org.jquantlib.Settings().setEvaluationDate(today.add(1));
                                                    value_p = option.NPV();
                                                    new org.jquantlib.Settings().setEvaluationDate(today);
                                                    expected.put("theta", (value_p - value_m) / dT);

                                                    for (final String greek : calculated.keySet()) {
                                                        final double expct = expected.get(greek);
                                                        final double calcl = calculated.get(greek);
                                                        final double tol = tolerance.get(greek);
                                                        final double error = Utilities.relativeError(expct, calcl, u);
                                                        if (error > tol) {
                                                            fail("failed to reproduce quanto-option Greek " + greek + ":"
                                                                    + "\n    expected:   " + expct
                                                                    + "\n    calculated: " + calcl
                                                                    + "\n    error:      " + error
                                                                    + "\n    tolerance:  " + tol
                                                                    + "\n    type=" + type + " strike=" + strike
                                                                    + " u=" + u + " q=" + q + " r=" + r
                                                                    + " v=" + v + " fxr=" + fxr + " fxv=" + fxv
                                                                    + " corr=" + corr);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Single C++ {@code QuantoForwardOptionData} row. */
    private static final class QuantoForwardOptionData {
        final Option.Type type;
        final double moneyness;
        final double s, q, r, start, t, v, fxr, fxv, corr, result, tol;
        QuantoForwardOptionData(final Option.Type type, final double moneyness,
                                final double s, final double q, final double r,
                                final double start, final double t,
                                final double v, final double fxr, final double fxv,
                                final double corr, final double result, final double tol) {
            this.type = type; this.moneyness = moneyness;
            this.s = s; this.q = q; this.r = r;
            this.start = start; this.t = t;
            this.v = v; this.fxr = fxr; this.fxv = fxv;
            this.corr = corr; this.result = result; this.tol = tol;
        }
    }

    @Test
    public void testForwardValues() {
        QL.info("Testing quanto-forward option values...");
        // Java port of v1.42.1 test-suite/quantooption.cpp::testForwardValues.

        final QuantoForwardOptionData[] values = {
            // reset=0.0, quanto (not-forward) cases — match testValues numbers
            new QuantoForwardOptionData(Option.Type.Call, 1.05, 100.0, 0.04, 0.08,
                    0.00, 0.5, 0.20, 0.05, 0.10, 0.3, 5.3280 / 1.5, 1.0e-4),
            new QuantoForwardOptionData(Option.Type.Put, 1.05, 100.0, 0.04, 0.08,
                    0.00, 0.5, 0.20, 0.05, 0.10, 0.3, 8.1636, 1.0e-4),
            // reset!=0.0, quanto-forward (cursorily checked vs FinCAD 7)
            new QuantoForwardOptionData(Option.Type.Call, 1.05, 100.0, 0.04, 0.08,
                    0.25, 0.5, 0.20, 0.05, 0.10, 0.3, 2.0171, 1.0e-4),
            new QuantoForwardOptionData(Option.Type.Put, 1.05, 100.0, 0.04, 0.08,
                    0.25, 0.5, 0.20, 0.05, 0.10, 0.3, 6.7296, 1.0e-4)
        };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(today, qRate, dc));
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(today, rRate, dc));
        final SimpleQuote vol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                Utilities.flatVol(today, vol, dc));

        final SimpleQuote fxRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> fxrTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(today, fxRate, dc));
        final SimpleQuote fxVol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> fxVolTS = new Handle<BlackVolTermStructure>(
                Utilities.flatVol(today, fxVol, dc));
        final SimpleQuote correlation = new SimpleQuote(0.0);

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<Quote>(spot), qTS, rTS, volTS);
        final PricingEngine engine = new QuantoForwardVanillaEngine(
                stochProcess, fxrTS, fxVolTS, new Handle<Quote>(correlation));

        for (final QuantoForwardOptionData v : values) {
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(v.type, 0.0);
            final Date exDate = today.add(timeToDays(v.t));
            final Exercise exercise = new EuropeanExercise(exDate);
            final Date reset = today.add(timeToDays(v.start));

            spot.setValue(v.s);
            qRate.setValue(v.q);
            rRate.setValue(v.r);
            vol.setValue(v.v);
            fxRate.setValue(v.fxr);
            fxVol.setValue(v.fxv);
            correlation.setValue(v.corr);

            final QuantoForwardVanillaOption option = new QuantoForwardVanillaOption(
                    v.moneyness, reset, payoff, exercise);
            option.setPricingEngine(engine);

            final double calculated = option.NPV();
            final double error = Math.abs(calculated - v.result);
            final double tolerance = 1.0e-4;
            if (error > tolerance) {
                fail("failed to reproduce quanto-forward-option value:"
                        + "\n    expected:   " + v.result
                        + "\n    calculated: " + calculated
                        + "\n    error:      " + error
                        + "\n    tolerance:  " + tolerance
                        + "\n    type=" + v.type + " moneyness=" + v.moneyness
                        + " s=" + v.s + " corr=" + v.corr + " start=" + v.start);
            }
        }
    }

    @Test
    public void testForwardGreeks() {
        QL.info("Testing quanto-forward option greeks...");
        // Java port of v1.42.1 test-suite/quantooption.cpp::testForwardGreeks.

        final java.util.Map<String, Double> calculated = new java.util.HashMap<>();
        final java.util.Map<String, Double> expected = new java.util.HashMap<>();
        final java.util.Map<String, Double> tolerance = new java.util.HashMap<>();
        tolerance.put("delta",   1.0e-5);
        tolerance.put("gamma",   1.0e-5);
        tolerance.put("theta",   1.0e-5);
        tolerance.put("rho",     1.0e-5);
        tolerance.put("divRho",  1.0e-5);
        tolerance.put("vega",    1.0e-5);
        tolerance.put("qrho",    1.0e-5);
        tolerance.put("qvega",   1.0e-5);
        tolerance.put("qlambda", 1.0e-5);

        final Option.Type[] types = { Option.Type.Call, Option.Type.Put };
        final double[] moneyness = { 0.9, 1.0, 1.1 };
        final double[] underlyings = { 100.0 };
        final double[] qRates = { 0.04, 0.05 };
        final double[] rRates = { 0.01, 0.05, 0.15 };
        final int[] lengths = { 2 };
        final int[] startMonths = { 6, 9 };
        final double[] vols = { 0.11, 1.20 };
        final double[] correlations = { 0.10, 0.90 };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new org.jquantlib.Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(qRate, dc));
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(rRate, dc));
        final SimpleQuote vol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                Utilities.flatVol(vol, dc));
        final SimpleQuote fxRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> fxrTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(fxRate, dc));
        final SimpleQuote fxVol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> fxVolTS = new Handle<BlackVolTermStructure>(
                Utilities.flatVol(fxVol, dc));
        final SimpleQuote correlation = new SimpleQuote(0.0);

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<Quote>(spot), qTS, rTS, volTS);
        final PricingEngine engine = new QuantoForwardVanillaEngine(
                stochProcess, fxrTS, fxVolTS, new Handle<Quote>(correlation));

        for (final Option.Type type : types) {
            for (final double mn : moneyness) {
                for (final int length : lengths) {
                    for (final int startMonth : startMonths) {
                        final Date exDate = today.add(new org.jquantlib.time.Period(
                                length, org.jquantlib.time.TimeUnit.Years));
                        final Exercise exercise = new EuropeanExercise(exDate);
                        final Date reset = today.add(new org.jquantlib.time.Period(
                                startMonth, org.jquantlib.time.TimeUnit.Months));
                        final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, 0.0);

                        final QuantoForwardVanillaOption option = new QuantoForwardVanillaOption(
                                mn, reset, payoff, exercise);
                        option.setPricingEngine(engine);

                        for (final double u : underlyings) {
                            for (final double m : qRates) {
                                for (final double n : rRates) {
                                    for (final double v : vols) {
                                        for (final double fxr : rRates) {
                                            for (final double fxv : vols) {
                                                for (final double corr : correlations) {
                                                    final double q = m, r = n;
                                                    spot.setValue(u);
                                                    qRate.setValue(q);
                                                    rRate.setValue(r);
                                                    vol.setValue(v);
                                                    fxRate.setValue(fxr);
                                                    fxVol.setValue(fxv);
                                                    correlation.setValue(corr);

                                                    final double value = option.NPV();
                                                    calculated.put("delta",   option.delta());
                                                    calculated.put("gamma",   option.gamma());
                                                    calculated.put("theta",   option.theta());
                                                    calculated.put("rho",     option.rho());
                                                    calculated.put("divRho",  option.dividendRho());
                                                    calculated.put("vega",    option.vega());
                                                    calculated.put("qrho",    option.qrho());
                                                    calculated.put("qvega",   option.qvega());
                                                    calculated.put("qlambda", option.qlambda());

                                                    if (value > spot.value() * 1.0e-5) {
                                                        final double du = u * 1.0e-4;
                                                        spot.setValue(u + du);
                                                        double value_p = option.NPV();
                                                        final double delta_p = option.delta();
                                                        spot.setValue(u - du);
                                                        double value_m = option.NPV();
                                                        final double delta_m = option.delta();
                                                        spot.setValue(u);
                                                        expected.put("delta", (value_p - value_m) / (2 * du));
                                                        expected.put("gamma", (delta_p - delta_m) / (2 * du));

                                                        final double dr = r * 1.0e-4;
                                                        rRate.setValue(r + dr);
                                                        value_p = option.NPV();
                                                        rRate.setValue(r - dr);
                                                        value_m = option.NPV();
                                                        rRate.setValue(r);
                                                        expected.put("rho", (value_p - value_m) / (2 * dr));

                                                        final double dq = q * 1.0e-4;
                                                        qRate.setValue(q + dq);
                                                        value_p = option.NPV();
                                                        qRate.setValue(q - dq);
                                                        value_m = option.NPV();
                                                        qRate.setValue(q);
                                                        expected.put("divRho", (value_p - value_m) / (2 * dq));

                                                        final double dv = v * 1.0e-4;
                                                        vol.setValue(v + dv);
                                                        value_p = option.NPV();
                                                        vol.setValue(v - dv);
                                                        value_m = option.NPV();
                                                        vol.setValue(v);
                                                        expected.put("vega", (value_p - value_m) / (2 * dv));

                                                        final double dfxr = fxr * 1.0e-4;
                                                        fxRate.setValue(fxr + dfxr);
                                                        value_p = option.NPV();
                                                        fxRate.setValue(fxr - dfxr);
                                                        value_m = option.NPV();
                                                        fxRate.setValue(fxr);
                                                        expected.put("qrho", (value_p - value_m) / (2 * dfxr));

                                                        final double dfxv = fxv * 1.0e-4;
                                                        fxVol.setValue(fxv + dfxv);
                                                        value_p = option.NPV();
                                                        fxVol.setValue(fxv - dfxv);
                                                        value_m = option.NPV();
                                                        fxVol.setValue(fxv);
                                                        expected.put("qvega", (value_p - value_m) / (2 * dfxv));

                                                        final double dcorr = corr * 1.0e-4;
                                                        correlation.setValue(corr + dcorr);
                                                        value_p = option.NPV();
                                                        correlation.setValue(corr - dcorr);
                                                        value_m = option.NPV();
                                                        correlation.setValue(corr);
                                                        expected.put("qlambda", (value_p - value_m) / (2 * dcorr));

                                                        final double dT = dc.yearFraction(today.sub(1), today.add(1));
                                                        new org.jquantlib.Settings().setEvaluationDate(today.sub(1));
                                                        value_m = option.NPV();
                                                        new org.jquantlib.Settings().setEvaluationDate(today.add(1));
                                                        value_p = option.NPV();
                                                        new org.jquantlib.Settings().setEvaluationDate(today);
                                                        expected.put("theta", (value_p - value_m) / dT);

                                                        for (final String greek : calculated.keySet()) {
                                                            final double expct = expected.get(greek);
                                                            final double calcl = calculated.get(greek);
                                                            final double tol = tolerance.get(greek);
                                                            final double error = Utilities.relativeError(expct, calcl, u);
                                                            if (error > tol) {
                                                                fail("failed to reproduce quanto-forward Greek " + greek + ":"
                                                                        + "\n    expected:   " + expct
                                                                        + "\n    calculated: " + calcl
                                                                        + "\n    error:      " + error
                                                                        + "\n    tolerance:  " + tol
                                                                        + "\n    type=" + type + " moneyness=" + mn
                                                                        + " u=" + u + " q=" + q + " r=" + r
                                                                        + " v=" + v + " fxr=" + fxr + " fxv=" + fxv
                                                                        + " corr=" + corr);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Ignore(REASON_FORWARD + " — performance-style discounted-strike variant")
    @Test
    public void testForwardPerformanceValues() { fail("not implemented"); }

    @Ignore(REASON_BARRIER)
    @Test
    public void testBarrierValues() { fail("not implemented"); }

    @Ignore(REASON_FDM_HELPER)
    @Test
    public void testFDMQuantoHelper() { fail("not implemented"); }

    @Ignore(REASON_FDM_HELPER + " — European PDE quanto FD vs analytic")
    @Test
    public void testPDEOptionValues() { fail("not implemented"); }

    @Ignore(REASON_AMERICAN)
    @Test
    public void testAmericanQuantoOption() { fail("not implemented"); }

    @Ignore(REASON_DOUBLE_BARRIER)
    @Test
    public void testDoubleBarrierValues() { fail("not implemented"); }
}
