/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 5h.5-SLV — FdmHestonFwdOp cross-validation tests.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences.operators;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmHestonFwdOp;
import org.jquantlib.methods.finitedifferences.operators.FdmSquareRootFwdOp.TransformationType;
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
 * Cross-validation of {@link FdmHestonFwdOp} against C++ v1.42.1
 * via {@code migration-harness/references/heston-slv/fdm_heston_fwd_op.json}.
 *
 * <p>Tier: TIGHT (1e-8 abs / 1e-10 rel) — analytic operator with no iteration.
 */
public class FdmHestonFwdOpTest {

    private static final double TOL_ABS = 1e-8;
    private static final double TOL_REL = 1e-10;

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
                                         final JSONArray exp, final Array act) {
        assertEquals(name + ".size", exp.length(), act.size());
        for (int i = 0; i < exp.length(); ++i) {
            final double e = exp.getDouble(i);
            final double a = act.get(i);
            final double diff = Math.abs(e - a);
            if (diff <= TOL_ABS) continue;
            final double rel = diff / Math.max(Math.abs(e), 1e-300);
            assertEquals(name + "[" + i + "] (rel=" + rel + ")",
                    e, a, Math.max(TOL_ABS, TOL_REL * Math.abs(e)));
        }
    }

    @Test
    public void testPlainApply() {
        final ReferenceReader rr = ReferenceReader.load("heston-slv/fdm_heston_fwd_op");
        final ReferenceReader.Case c = rr.getCase("plain_apply");
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
        final double vMin = in.getDouble("vMin");
        final double vMax = in.getDouble("vMax");
        final int nv = in.getInt("nv");

        final HestonProcess process = buildProcess(r, q, s0, v0, kappa, theta, sigma, rho);
        final FdmMesher mesher = mesher2D(xMin, xMax, nx, vMin, vMax, nv);

        final FdmHestonFwdOp op = new FdmHestonFwdOp(mesher, process,
                TransformationType.Plain);
        op.setTime(0.0, 1.0);

        final int N = nx * nv;
        final Array p1 = new Array(N).fill(1.0);
        final Array p2 = new Array(N);
        final Array vSpread = new Array(N);
        final Array vConcen = new Array(N);
        for (int j = 0; j < nv; ++j) {
            for (int i = 0; i < nx; ++i) {
                final double x = mesher.locations(0).get(i + j * nx);
                final double v = mesher.locations(1).get(i + j * nx);
                p2.set(i + j * nx, Math.sin(x));
                vSpread.set(i + j * nx, Math.exp(-(x - Math.log(s0)) * (x - Math.log(s0))));
                vConcen.set(i + j * nx, Math.exp(-(v - 0.04) * (v - 0.04) / 0.001));
            }
        }

        final JSONObject exp = (JSONObject) c.expectedRaw();
        assertArrayClose("apply_constant", exp.getJSONArray("apply_constant"), op.apply(p1));
        assertArrayClose("apply_sin",      exp.getJSONArray("apply_sin"),      op.apply(p2));
        assertArrayClose("apply_xspread",  exp.getJSONArray("apply_xspread"),  op.apply(vSpread));
        assertArrayClose("apply_vconcen",  exp.getJSONArray("apply_vconcen"),  op.apply(vConcen));
        assertArrayClose("applyMixed_constant",
                exp.getJSONArray("applyMixed_constant"), op.applyMixed(p1));
        assertArrayClose("applyDirection0",
                exp.getJSONArray("applyDirection0"), op.applyDirection(0, p2));
        assertArrayClose("applyDirection1",
                exp.getJSONArray("applyDirection1"), op.applyDirection(1, p2));
    }
}
