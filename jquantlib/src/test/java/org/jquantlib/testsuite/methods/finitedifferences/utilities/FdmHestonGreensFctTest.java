/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 5h.5-SLV-b — FdmHestonGreensFct cross-validation tests.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmSquareRootFwdOp.TransformationType;
import org.jquantlib.methods.finitedifferences.utilities.FdmHestonGreensFct;
import org.jquantlib.methods.finitedifferences.utilities.FdmHestonGreensFct.Algorithm;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validation of {@link FdmHestonGreensFct} against C++ v1.42.1
 * via {@code migration-harness/references/heston-slv/fdm_heston_greens_fct.json}.
 *
 * <p>Tier-stratified tolerances:
 * <ul>
 *   <li>Gaussian — TIGHT (1e-10 abs / 1e-12 rel). Closed-form bivariate
 *       Gaussian, no underlying iteration.</li>
 *   <li>ZeroCorrelation — LOOSE (1e-4 abs/rel). The variance marginal
 *       depends on {@link org.jquantlib.methods.finitedifferences.utilities.SquareRootProcessRNDCalculator#pdf}
 *       which uses a CDF central-difference approximation pending the
 *       non-central chi-squared closed form (carry-forward documented at
 *       its source). The 1e-4 tier matches that carry-forward's stated
 *       tolerance.</li>
 * </ul>
 */
public class FdmHestonGreensFctTest {

    private static final double TOL_GAUSS_ABS = 1e-10;
    private static final double TOL_GAUSS_REL = 1e-12;
    private static final double TOL_ZC_ABS = 1e-4;
    private static final double TOL_ZC_REL = 1e-4;

    private static FdmMesher mesher2D(final double xMin, final double xMax, final int nx,
                                      final double vMin, final double vMax, final int nv) {
        final List<Fdm1dMesher> ms = new ArrayList<>(2);
        ms.add(new Uniform1dMesher(xMin, xMax, nx));
        ms.add(new Uniform1dMesher(vMin, vMax, nv));
        return new FdmMesherComposite(ms);
    }

    private static HestonProcess buildProcess(final double r, final double q,
                                              final double s0, final double v0,
                                              final double kappa, final double theta,
                                              final double sigma, final double rho) {
        final Date today = new Date(15, Month.May, 2026);
        final DayCounter dc = new Actual365Fixed();
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, r, dc));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, q, dc));
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(s0));
        return new HestonProcess(rTS, qTS, spot, v0, kappa, theta, sigma, rho);
    }

    private static void assertArrayClose(final String name,
                                         final JSONArray exp, final Array act,
                                         final double absTol, final double relTol) {
        assertEquals(name + ".size", exp.length(), act.size());
        for (int i = 0; i < exp.length(); ++i) {
            final double e = exp.getDouble(i);
            final double a = act.get(i);
            final double diff = Math.abs(e - a);
            if (diff <= absTol) continue;
            final double tol = Math.max(absTol, relTol * Math.abs(e));
            assertEquals(name + "[" + i + "]", e, a, tol);
        }
    }

    private static void runCase(final String caseName, final TransformationType trafo,
                                final boolean logVariance) {
        final ReferenceReader rr = ReferenceReader.load("heston-slv/fdm_heston_greens_fct");
        final ReferenceReader.Case c = rr.getCase(caseName);
        final JSONObject in = c.inputs();

        final double r = in.getDouble("r");
        final double q = in.getDouble("q");
        final double s0 = in.getDouble("s0");
        final double v0 = in.getDouble("v0");
        final double kappa = in.getDouble("kappa");
        final double theta = in.getDouble("theta");
        final double sigma = in.getDouble("sigma");
        final double rho = in.getDouble("rho");
        final double xMin = in.getDouble("xMin");
        final double xMax = in.getDouble("xMax");
        final int nx = in.getInt("nx");
        final int nv = in.getInt("nv");
        final double l0 = in.getDouble("l0");
        final double t = in.getDouble("t");

        final double vMin, vMax;
        if (logVariance) {
            vMin = in.getDouble("vLogMin");
            vMax = in.getDouble("vLogMax");
        } else {
            vMin = in.getDouble("vMin");
            vMax = in.getDouble("vMax");
        }

        final HestonProcess process = buildProcess(r, q, s0, v0, kappa, theta, sigma, rho);
        final FdmMesher mesher = mesher2D(xMin, xMax, nx, vMin, vMax, nv);

        final FdmHestonGreensFct gf = new FdmHestonGreensFct(mesher, process, trafo, l0);
        final JSONObject exp = (JSONObject) c.expectedRaw();
        assertArrayClose("zero_correlation", exp.getJSONArray("zero_correlation"),
                gf.get(t, Algorithm.ZeroCorrelation), TOL_ZC_ABS, TOL_ZC_REL);
        assertArrayClose("gaussian", exp.getJSONArray("gaussian"),
                gf.get(t, Algorithm.Gaussian), TOL_GAUSS_ABS, TOL_GAUSS_REL);
    }

    @Test
    public void testPlainShortTime() {
        runCase("plain_t01", TransformationType.Plain, /*logVariance=*/ false);
    }

    @Test
    public void testPlainHalfYear() {
        runCase("plain_t05", TransformationType.Plain, /*logVariance=*/ false);
    }

    @Test
    public void testPowerTransform() {
        runCase("power_t025", TransformationType.Power, /*logVariance=*/ false);
    }

    @Test
    public void testLogTransform() {
        runCase("log_t025", TransformationType.Log, /*logVariance=*/ true);
    }

    @Test
    public void testSemiAnalyticalCarryForward() {
        // SemiAnalytical algorithm needs HestonProcess.pdf() which is not yet ported;
        // the get() call must raise UnsupportedOperationException with a clear marker.
        final HestonProcess process = buildProcess(0.03, 0.0, 100.0,
                0.04, 2.5, 0.04, 0.2, -0.5);
        final FdmMesher mesher = mesher2D(Math.log(50.0), Math.log(200.0), 6,
                0.005, 0.5, 6);
        final FdmHestonGreensFct gf = new FdmHestonGreensFct(mesher, process,
                TransformationType.Plain);
        try {
            gf.get(0.1, Algorithm.SemiAnalytical);
            fail("SemiAnalytical should raise UnsupportedOperationException — "
                    + "Phase 5h.5-SLV-b carry-forward (HestonProcess.pdf)");
        } catch (final UnsupportedOperationException e) {
            // good
        }
    }
}
