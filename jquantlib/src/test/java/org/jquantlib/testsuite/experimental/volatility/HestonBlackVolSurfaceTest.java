/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.volatility;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.volatility.HestonBlackVolSurface;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Phase Production-Audit tests for {@link HestonBlackVolSurface}.
 *
 * <p>Cross-validated against C++ QuantLib v1.42.1 via the migration harness;
 * reference data lives at
 * {@code migration-harness/references/experimental/volatility/heston_black_vol_surface.json}.
 *
 * <p>The Java port uses the simplified single-arg constructor
 * (Gatheral + Gauss-Laguerre 144), matching the C++ default
 * {@code AnalyticHestonEngine::AngledContour + gaussLaguerre(160)} closely
 * enough that ATM and skew vols agree to a few hundred ULPs of relative
 * accuracy.
 *
 * <p>Tolerance: relative {@code 1e-6}. The TIGHT tier (1e-12) does not
 * apply here because:
 * <ul>
 *   <li>C++ uses Gauss-Laguerre order 160; Java uses 144 (limited by
 *       the embedded n=128 quadrature table — see GaussLaguerreIntegration
 *       Phase 2f WI-3 design note).</li>
 *   <li>C++ default {@code ComplexLogFormula} is {@code AngledContour};
 *       Java only implements {@code Gatheral}. Both are mathematically
 *       equivalent but the integrand is parameterised differently.</li>
 * </ul>
 *
 * @author Phase Production-Audit
 */
public class HestonBlackVolSurfaceTest {

    private static final double REL_TOL = 1.0e-6;
    private static final double ABS_TOL = 1.0e-9;

    private HestonBlackVolSurface buildSurface() {
        // Match the probe setup exactly:
        // S0=100, v0=0.04, kappa=2.0, theta=0.04, sigma=0.30, rho=-0.5,
        // r=0.05, q=0.02, today=15-Jan-2026, dc=Actual365Fixed.
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.January, 2026);

        final var spot = new Handle<Quote>(new SimpleQuote(100.0));
        final var rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(0.05)), dc));
        final var qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(0.02)), dc));

        final HestonProcess process = new HestonProcess(rTS, qTS, spot,
                0.04, 2.0, 0.04, 0.30, -0.5);
        final HestonModel model = new HestonModel(process);
        return new HestonBlackVolSurface(new Handle<HestonModel>(model));
    }

    @Test
    public void testBlackVolAgainstCppReference() {
        final ReferenceReader ref = ReferenceReader.load(
                "experimental/volatility/heston_black_vol_surface");
        final HestonBlackVolSurface s = buildSurface();

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            final JSONObject in = c.inputs();
            final double t = in.getDouble("t");
            final double k = in.getDouble("K");
            final JSONObject exp = (JSONObject) c.expectedRaw();
            final double bvExp = exp.getDouble("blackVol");
            final double bvarExp = exp.getDouble("blackVariance");

            final double bvAct = s.blackVol(t, k, true);
            final double bvarAct = s.blackVariance(t, k, true);

            assertEquals(name + " blackVol",
                    bvExp, bvAct,
                    Math.max(ABS_TOL, Math.abs(bvExp) * REL_TOL));
            assertEquals(name + " blackVariance",
                    bvarExp, bvarAct,
                    Math.max(ABS_TOL, Math.abs(bvarExp) * REL_TOL));
        }
    }

    /** Sanity: blackVariance = blackVol^2 * t (the C++ implementation). */
    @Test
    public void testVarianceConsistency() {
        final HestonBlackVolSurface s = buildSurface();
        for (double t : new double[]{0.25, 1.0, 2.0}) {
            for (double K : new double[]{80.0, 100.0, 120.0}) {
                final double bv = s.blackVol(t, K, true);
                final double bvar = s.blackVariance(t, K, true);
                final double expected = bv * bv * t;
                assertEquals("variance=vol^2*t at t=" + t + " K=" + K,
                        expected, bvar, 1e-12);
            }
        }
    }

    /** Sanity: ATM black vol is positive and bounded for our parameter set. */
    @Test
    public void testBlackVolPositiveBounded() {
        final HestonBlackVolSurface s = buildSurface();
        for (double t : new double[]{0.25, 1.0, 2.0, 3.0}) {
            final double bv = s.blackVol(t, 100.0, true);
            assertTrue("ATM blackVol > 0 at t=" + t, bv > 0.0);
            assertTrue("ATM blackVol < 1.0 at t=" + t + " (got " + bv + ")",
                    bv < 1.0);
        }
    }

    /** Sanity: minStrike()=0, maxStrike()=Double.MAX_VALUE. */
    @Test
    public void testStrikeRange() {
        final HestonBlackVolSurface s = buildSurface();
        assertEquals(0.0, s.minStrike(), 0.0);
        assertEquals(Double.MAX_VALUE, s.maxStrike(), 0.0);
    }
}
