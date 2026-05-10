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
import org.jquantlib.instruments.QuantoVanillaOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.PricingEngine;
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

    @Ignore(REASON_VANILLA + " + Greeks numerical-derivative cross-check")
    @Test
    public void testGreeks() { fail("not implemented"); }

    @Ignore(REASON_FORWARD)
    @Test
    public void testForwardValues() { fail("not implemented"); }

    @Ignore(REASON_FORWARD + " + Greeks numerical-derivative cross-check")
    @Test
    public void testForwardGreeks() { fail("not implemented"); }

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
