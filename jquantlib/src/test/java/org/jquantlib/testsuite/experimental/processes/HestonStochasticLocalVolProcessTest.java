/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 5h.5-SLV — HestonStochasticLocalVolProcess cross-validation tests.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.processes;

import static org.junit.Assert.assertEquals;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.processes.HestonStochasticLocalVolProcess;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.LocalConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validation of {@link HestonStochasticLocalVolProcess} (Phase 5h.5-SLV WI-3).
 *
 * <p>Tier: TIGHT (1e-9 abs / 1e-12 rel) for drift/diffusion (analytic).
 * LOOSE (1e-9 abs) for evolve (single deterministic dw — no MC iteration).
 */
public class HestonStochasticLocalVolProcessTest {

    private static final double TOL_ABS = 1e-9;
    private static final double TOL_REL = 1e-12;

    private static HestonStochasticLocalVolProcess buildProcess(final JSONObject in,
                                                                final double L) {
        final Date today = new Date(15, Month.May, 2026);
        final DayCounter dc = new Actual365Fixed();
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, in.getDouble("r"), dc));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, in.getDouble("q"), dc));
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(in.getDouble("s0")));
        final HestonProcess hp = new HestonProcess(rTS, qTS, spot,
                in.getDouble("v0"), in.getDouble("kappa"),
                in.getDouble("theta"), in.getDouble("sigma"), in.getDouble("rho"));
        final LocalVolTermStructure leverage = new LocalConstantVol(today, L, dc);
        return new HestonStochasticLocalVolProcess(hp, leverage, 1.0);
    }

    private static void assertClose(final String name, final double exp, final double act) {
        final double diff = Math.abs(exp - act);
        if (diff <= TOL_ABS) return;
        assertEquals(name, exp, act, Math.max(TOL_ABS, TOL_REL * Math.abs(exp)));
    }

    private static void assertArrayClose(final String name, final JSONArray exp, final Array act) {
        assertEquals(name + ".size", exp.length(), act.size());
        for (int i = 0; i < exp.length(); ++i) {
            assertClose(name + "[" + i + "]", exp.getDouble(i), act.get(i));
        }
    }

    @Test
    public void testDriftAndDiffusionAtm() {
        final ReferenceReader rr = ReferenceReader.load("heston-slv/heston_slv_process");
        final ReferenceReader.Case c = rr.getCase("drift_diffusion_at_atm");
        final JSONObject in = c.inputs();
        final double L = in.getDouble("L");
        final HestonStochasticLocalVolProcess slv = buildProcess(in, L);

        final Array x = new Array(2);
        x.set(0, in.getJSONArray("x").getDouble(0));
        x.set(1, in.getJSONArray("x").getDouble(1));

        final Array d = slv.drift(in.getDouble("t"), x);
        final Matrix s = slv.diffusion(in.getDouble("t"), x);

        final JSONObject exp = (JSONObject) c.expectedRaw();
        assertArrayClose("drift", exp.getJSONArray("drift"), d);

        final JSONArray diffArr = exp.getJSONArray("diffusion");
        assertEquals("diffusion.rows", diffArr.length(), s.rows());
        for (int i = 0; i < diffArr.length(); ++i) {
            final JSONArray row = diffArr.getJSONArray(i);
            for (int k = 0; k < row.length(); ++k) {
                assertClose("diffusion[" + i + "][" + k + "]",
                        row.getDouble(k), s.get(i, k));
            }
        }
    }

    @Test
    public void testDriftAndDiffusionOtmHighV() {
        final ReferenceReader rr = ReferenceReader.load("heston-slv/heston_slv_process");
        final ReferenceReader.Case c = rr.getCase("drift_diffusion_otm_high_v");
        final JSONObject in = c.inputs();
        final double L = in.getDouble("L");
        final HestonStochasticLocalVolProcess slv = buildProcess(in, L);

        final Array x = new Array(2);
        x.set(0, in.getJSONArray("x").getDouble(0));
        x.set(1, in.getJSONArray("x").getDouble(1));

        final Array d = slv.drift(in.getDouble("t"), x);
        final Matrix s = slv.diffusion(in.getDouble("t"), x);

        final JSONObject exp = (JSONObject) c.expectedRaw();
        assertArrayClose("drift", exp.getJSONArray("drift"), d);

        final JSONArray diffArr = exp.getJSONArray("diffusion");
        assertEquals("diffusion.rows", diffArr.length(), s.rows());
        for (int i = 0; i < diffArr.length(); ++i) {
            final JSONArray row = diffArr.getJSONArray(i);
            for (int k = 0; k < row.length(); ++k) {
                assertClose("diffusion[" + i + "][" + k + "]",
                        row.getDouble(k), s.get(i, k));
            }
        }
    }

    @Test
    public void testEvolveSmallDt() {
        final ReferenceReader rr = ReferenceReader.load("heston-slv/heston_slv_process");
        final ReferenceReader.Case c = rr.getCase("evolve_small_dt");
        final JSONObject in = c.inputs();
        final double L = in.getDouble("L");
        final HestonStochasticLocalVolProcess slv = buildProcess(in, L);

        final Array x0 = new Array(2);
        x0.set(0, in.getJSONArray("x0").getDouble(0));
        x0.set(1, in.getJSONArray("x0").getDouble(1));
        final Array dw = new Array(2);
        dw.set(0, in.getJSONArray("dw").getDouble(0));
        dw.set(1, in.getJSONArray("dw").getDouble(1));

        final Array r = slv.evolve(in.getDouble("t0"), x0, in.getDouble("dt"), dw);
        assertArrayClose("evolve_small_dt", c.expectedArray(), r);
    }

    @Test
    public void testEvolveLongDt() {
        final ReferenceReader rr = ReferenceReader.load("heston-slv/heston_slv_process");
        final ReferenceReader.Case c = rr.getCase("evolve_long_dt");
        final JSONObject in = c.inputs();
        final double L = in.getDouble("L");
        final HestonStochasticLocalVolProcess slv = buildProcess(in, L);

        final Array x0 = new Array(2);
        x0.set(0, in.getJSONArray("x0").getDouble(0));
        x0.set(1, in.getJSONArray("x0").getDouble(1));
        final Array dw = new Array(2);
        dw.set(0, in.getJSONArray("dw").getDouble(0));
        dw.set(1, in.getJSONArray("dw").getDouble(1));

        final Array r = slv.evolve(in.getDouble("t0"), x0, in.getDouble("dt"), dw);
        assertArrayClose("evolve_long_dt", c.expectedArray(), r);
    }
}
