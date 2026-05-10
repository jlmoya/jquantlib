/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.processes;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticBSMHullWhiteEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonHullWhiteEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5h skeleton port of {@code test-suite/hybridhestonhullwhiteprocess.cpp}
 * v1.42.1 (1,418 LOC, 13 test cases).
 *
 * <p>The thirteen C++ tests exercise the equity / interest-rate hybrid
 * model {@code HybridHestonHullWhiteProcess} and its associated pricing
 * engines:
 * <ul>
 *   <li>{@code testBsmHullWhiteEngine} — collapses Heston→BSM by setting
 *       vol-of-vol to zero, then prices a vanilla call under
 *       {@code AnalyticBSMHullWhiteEngine} and compares to a calibrated
 *       Hull-White short-rate model with deterministic equity vol.</li>
 *   <li>{@code testCompareBsmHWandHestonHW} — confirms
 *       {@code AnalyticHestonHullWhiteEngine} ≈
 *       {@code AnalyticBSMHullWhiteEngine} in the BSM limit.</li>
 *   <li>{@code testZeroBondPricing} — MC simulation of zero-coupon bond
 *       prices via the hybrid process with Andersen QE discretization.</li>
 *   <li>{@code testMcVanillaPricing} — MC vanilla-call pricing under the
 *       full hybrid process; cross-checks against analytic engine.</li>
 *   <li>{@code testMcPureHestonPricing} — degenerate hybrid (HW vol = 0)
 *       reduces to pure Heston; MC matches {@code AnalyticHestonEngine}.</li>
 *   <li>{@code testAnalyticHestonHullWhitePricing} — analytic engine
 *       cross-validation against MC.</li>
 *   <li>{@code testCallableEquityPricing} — prices a callable equity
 *       structure on the hybrid model.</li>
 *   <li>{@code testDiscretizationError} — tracks MC error vs. step size
 *       to confirm Andersen QE strong-order-1 convergence.</li>
 *   <li>{@code testFdmHestonHullWhiteEngine} — exercises
 *       {@code FdHestonHullWhiteVanillaEngine} (the only HHW engine
 *       already ported to Java; cf. Phase 2m).</li>
 *   <li>{@code testBsmHullWhitePricing} — BSM-HW analytic engine
 *       reference pricing.</li>
 *   <li>{@code testSpatialDiscretizatinError} — FD spatial-grid
 *       convergence study.</li>
 *   <li>{@code testHestonHullWhiteCalibration} — calibrate Heston piece
 *       of the hybrid model to vanilla quotes; CPU-intensive
 *       (Phase 5 META D8 — slow tag).</li>
 *   <li>{@code testH1HWPricingEngine} — Andersen / Piterbarg H1-HW
 *       expansion engine sanity check.</li>
 * </ul>
 *
 * <p><strong>Phase 5h.5 carry-forward:</strong> Java has only the
 * <em>finite-difference</em> Heston-HullWhite stack ported (Phase 2m):
 * {@code FdmHestonHullWhiteOp}, {@code FdmHestonHullWhiteSolver},
 * {@code FdHestonHullWhiteVanillaEngine}.
 *
 * <p>Missing classes that block all 13 tests:
 * <ul>
 *   <li>{@code HybridHestonHullWhiteProcess} — the joint stochastic
 *       process (3D: equity, variance, short rate);</li>
 *   <li>{@code AnalyticBSMHullWhiteEngine} — closed-form pricing under
 *       BSM equity + Hull-White rates;</li>
 *   <li>{@code AnalyticHestonHullWhiteEngine} — semi-analytic Heston +
 *       Hull-White pricing;</li>
 *   <li>{@code AnalyticH1HWEngine} — Andersen-Piterbarg H1-HW
 *       expansion;</li>
 *   <li>{@code MCHestonHullWhiteEngine} — Monte-Carlo hybrid pricer;</li>
 *   <li>HW-Heston correlation calibration helper;</li>
 *   <li>{@code MCVanillaEngine}-style callable-equity pricer.</li>
 * </ul>
 *
 * <p>Once {@code FdHestonHullWhiteVanillaEngine}-only coverage is
 * needed independently, {@code testFdmHestonHullWhiteEngine} could be
 * implemented; however its cross-validation in the C++ file relies on
 * an analytic-engine reference, so it is also deferred until the
 * analytic engine is ported.
 *
 * <p>Source: {@code test-suite/hybridhestonhullwhiteprocess.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class HybridHestonHullWhiteProcessTest {

    private static final String REASON =
            "Phase 5h.5 — requires HybridHestonHullWhiteProcess + analytic / MC HHW engines "
            + "(Phase 2m / 4n carry-forward; only FdHestonHullWhite-stack exists in Java).";

    private static final String REASON_SLOW =
            "Phase 5h.5 + slow — requires HHW calibration loop and @Tag(\"slow\") "
            + "(see Phase 5 META D8).";

    @Ignore(REASON)
    @Test
    public void testBsmHullWhiteEngine() { fail("not implemented"); }

    /**
     * Phase 5h.5-HHW-b: un-ignored. C++ uses ZeroCurve for the rates +
     * dividend curves; this Java port uses FlatForward (only flat-curve
     * rate-curve helpers are available without porting ZeroCurve), which
     * preserves the core test intent: at large kappa with v0=theta=vol^2
     * and tiny sigma_v the Heston-HW engine must collapse to the BSM-HW
     * engine. Tolerance is loose to account for the n=128 vs n=144
     * Gauss-Laguerre quadrature difference and the flat-vs-zero curve
     * shape difference.
     */
    @Test
    public void testCompareBsmHWandHestonHW() {
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.July, 2026);
        new Settings().setEvaluationDate(today);

        final SimpleQuote spotQ = new SimpleQuote(100.0);
        final Handle<Quote> spot = new Handle<Quote>(spotQ);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.04, dc));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.02, dc));
        final SimpleQuote vol = new SimpleQuote(0.25);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, new NullCalendar(),
                        new Handle<Quote>(vol), dc));

        final BlackScholesMertonProcess bsmProc = new BlackScholesMertonProcess(
                spot, qTS, rTS, volTS);

        final HestonProcess hestonProc = new HestonProcess(rTS, qTS, spot,
                vol.value() * vol.value(), 1.0, vol.value() * vol.value(),
                1e-4, 0.0);
        hestonProc.update();
        final HestonModel hestonModel = new HestonModel(hestonProc);

        final HullWhite hwModel = new HullWhite(rTS, 0.01, 0.01);
        final PricingEngine bsmHw = new AnalyticBSMHullWhiteEngine(0.0, bsmProc, hwModel);
        final PricingEngine hestonHw = new AnalyticHestonHullWhiteEngine(
                hestonModel, hestonProc, hwModel, 128);

        // Loose tolerance — Java GaussLaguerre is n=128 (vs C++ 144) and
        // the residual sigma_v=1e-4 perturbation is amplified through
        // the Fourier integration.
        final double tol = 5e-3;
        final double[] strike = { 0.5, 0.75, 0.8, 0.9, 1.0, 1.1, 1.2, 1.5, 2.0 };
        final int[] maturity = { 1, 2, 3, 5, 10 };
        final Option.Type[] types = { Option.Type.Put, Option.Type.Call };

        int n = 0;
        for (final Option.Type type : types) {
            for (final double j : strike) {
                for (final int l : maturity) {
                    final Date maturityDate = today.add(l * 365);
                    final Exercise exercise = new EuropeanExercise(maturityDate);
                    final double fwd = j * spotQ.value()
                            * qTS.currentLink().discount(maturityDate)
                            / rTS.currentLink().discount(maturityDate);
                    final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, fwd);

                    final EuropeanOption option = new EuropeanOption(payoff, exercise);
                    option.setPricingEngine(bsmHw);
                    final double calc = option.NPV();
                    option.setPricingEngine(hestonHw);
                    final double exp = option.NPV();

                    final double abs = Math.abs(calc - exp);
                    final double rel = abs / Math.max(Math.abs(calc), 1e-30);
                    assertTrue(
                            "type=" + type + " strike-mult=" + j + " mat=" + l + "y"
                            + " calc=" + calc + " exp=" + exp + " rel=" + rel,
                            rel < tol || abs < 0.01);
                    n++;
                }
            }
        }
        assertTrue("ran some cases (" + n + ")", n > 0);
    }

    @Ignore(REASON)
    @Test
    public void testZeroBondPricing() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testMcVanillaPricing() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testMcPureHestonPricing() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testAnalyticHestonHullWhitePricing() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testCallableEquityPricing() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testDiscretizationError() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testFdmHestonHullWhiteEngine() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testBsmHullWhitePricing() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testSpatialDiscretizatinError() { fail("not implemented"); }

    @Ignore(REASON_SLOW)
    @Test
    public void testHestonHullWhiteCalibration() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testH1HWPricingEngine() { fail("not implemented"); }
}
