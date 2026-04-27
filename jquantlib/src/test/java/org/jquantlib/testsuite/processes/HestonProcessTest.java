/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for HestonProcess QuadraticExponential / QuadraticExponentialMartingale.
 See phase2a-plan §WI-3 (QE) and phase2b-design §3.1 WI-1 (QEM).
 */
package org.jquantlib.testsuite.processes;

import org.jquantlib.Settings;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Cross-validated tests for the QuadraticExponential and
 * QuadraticExponentialMartingale branches of {@link HestonProcess#evolve}
 * against v1.42.1 via
 * {@code migration-harness/references/processes/hestonprocess_qe.json}.
 */
public class HestonProcessTest {

    @Test
    public void qe_psiLow_centralVol() {
        runCase("qe_psiLow_centralVol");
    }

    @Test
    public void qe_psiHigh_lowInitV() {
        runCase("qe_psiHigh_lowInitV");
    }

    @Test
    public void qe_psiHigh_zeroVarianceDraw() {
        runCase("qe_psiHigh_zeroVarianceDraw");
    }

    @Test
    public void qem_psiLow_centralVol() {
        runCase("qem_psiLow_centralVol",
                HestonProcess.Discretization.QuadraticExponentialMartingale);
    }

    @Test
    public void qem_psiHigh_lowInitV() {
        runCase("qem_psiHigh_lowInitV",
                HestonProcess.Discretization.QuadraticExponentialMartingale);
    }

    // --- NonCentralChiSquareVariance (Phase 2d WI-2) -----------------------

    @Test
    public void nccv_midNcp() {
        runNccvCase("nccv_midNcp");
    }

    @Test
    public void nccv_lowNcp_lowV0() {
        runNccvCase("nccv_lowNcp_lowV0");
    }

    @Test
    public void nccv_highV0() {
        runNccvCase("nccv_highV0");
    }

    @Test
    public void nccv_pureMean() {
        runNccvCase("nccv_pureMean");
    }

    @Test
    public void nccv_highV0_dingRegion() {
        runNccvCase("nccv_highV0_dingRegion");
    }

    // --- BroadieKaya exact schemes (Phase 2f WI-3 C.4-C.6) ---------------

    @Test
    public void bk_lobatto_midV0()  { runBkCase("bk_lobatto_midV0",  HestonProcess.Discretization.BroadieKayaExactSchemeLobatto); }
    @Test
    public void bk_lobatto_lowV0()  { runBkCase("bk_lobatto_lowV0",  HestonProcess.Discretization.BroadieKayaExactSchemeLobatto); }
    @Test
    public void bk_lobatto_highV0() { runBkCase("bk_lobatto_highV0", HestonProcess.Discretization.BroadieKayaExactSchemeLobatto); }

    @Test
    public void bk_laguerre_midV0()  { runBkCase("bk_laguerre_midV0",  HestonProcess.Discretization.BroadieKayaExactSchemeLaguerre); }
    @Test
    public void bk_laguerre_lowV0()  { runBkCase("bk_laguerre_lowV0",  HestonProcess.Discretization.BroadieKayaExactSchemeLaguerre); }
    @Test
    public void bk_laguerre_highV0() { runBkCase("bk_laguerre_highV0", HestonProcess.Discretization.BroadieKayaExactSchemeLaguerre); }

    @Test
    public void bk_trapezoidal_midV0()  { runBkCase("bk_trapezoidal_midV0",  HestonProcess.Discretization.BroadieKayaExactSchemeTrapezoidal); }
    @Test
    public void bk_trapezoidal_lowV0()  { runBkCase("bk_trapezoidal_lowV0",  HestonProcess.Discretization.BroadieKayaExactSchemeTrapezoidal); }
    @Test
    public void bk_trapezoidal_highV0() { runBkCase("bk_trapezoidal_highV0", HestonProcess.Discretization.BroadieKayaExactSchemeTrapezoidal); }

    /**
     * BroadieKaya schemes drive (a) an inverse non-central chi-squared
     * variance draw and (b) a Brent root-find against a Fourier-inverted
     * CDF that depends on Math.exp / Math.cos / Math.sin and the
     * GammaFunction-driven modified Bessel I_nu series. Phase 2g WI-1
     * Brent alignment let the variance leg (a) tighten to TIGHT (same
     * surface as nccv_*); the asset leg (b) still compounds A13
     * 1-ULP-per-call drift between Math.* and libc++ across the
     * Fourier-CDF, so it stays at within(5e-3) per the per-test
     * inline justification at the assertion site.
     */
    private static void runBkCase(final String name,
                                  final HestonProcess.Discretization disc) {
        runFromGroupBk("processes/heston_broadiekaya", name, disc);
    }

    private static void runFromGroupBk(final String group, final String name,
                                       final HestonProcess.Discretization disc) {
        final ReferenceReader reader = ReferenceReader.load(group);
        final Case c = reader.getCase(name);
        final JSONObject in = c.inputs();
        final double r = in.getDouble("r");
        final double q = in.getDouble("q");
        final double s0 = in.getDouble("s0");
        final double v0 = in.getDouble("v0");
        final double kappa = in.getDouble("kappa");
        final double theta = in.getDouble("theta");
        final double sigma = in.getDouble("sigma");
        final double rho = in.getDouble("rho");
        final double t0 = in.getDouble("t0");
        final double dt = in.getDouble("dt");
        final JSONArray x0a = in.getJSONArray("x0");
        final JSONArray dwa = in.getJSONArray("dw");

        final Date today = new Date(22, Month.April, 2026);
        new Settings().setEvaluationDate(today);

        final YieldTermStructure rCurve = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(r)), new Actual365Fixed());
        final YieldTermStructure qCurve = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(q)), new Actual365Fixed());
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(s0));

        final HestonProcess process = new HestonProcess(
                new Handle<YieldTermStructure>(rCurve),
                new Handle<YieldTermStructure>(qCurve),
                spot, v0, kappa, theta, sigma, rho, disc);
        process.update();

        final Array x0 = new Array(new double[] { x0a.getDouble(0), x0a.getDouble(1) });
        // BroadieKaya uses a 3-factor draw; the nccv runner expects 2.
        final Array dw = new Array(new double[] {
                dwa.getDouble(0), dwa.getDouble(1), dwa.getDouble(2) });

        final Array evolved = process.evolve(t0, x0, dt, dw);

        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray evExp = exp.getJSONArray("evolved");
        // BroadieKaya per-test tolerance compromise (Phase 2f WI-3 A14
        // documentation, updated Phase 2g WI-1):
        // - The variance leg evolved[1] now sits at TIGHT post Phase 2g WI-1
        //   Brent.solveImpl alignment — same surface as nccv_*, which
        //   tightened in this commit's tier-promotion batch.
        // - The asset leg evolved[0] still depends on a Brent root-find
        //   against a Fourier-inverted CDF that iterates Math.exp / Math.cos
        //   / Math.sin / GammaFunction / ModifiedBesselFunction — every
        //   Math.* call accumulates the A13 1-ULP-per-call drift between
        //   Java and libc++. Empirical floor is ~2e-3 absolute on the
        //   asset price (~2e-5 relative). Tolerance.within at 5e-3 abs/rel
        //   absorbs that compound drift; structurally beyond LOOSE per A13.
        if (!Tolerance.tight(evolved.get(1), evExp.getDouble(1))) {
            fail(name + ".evolved[1]: exp=" + evExp.getDouble(1)
                    + " got=" + evolved.get(1)
                    + " Δ=" + Math.abs(evExp.getDouble(1) - evolved.get(1)));
        }
        if (!Tolerance.within(evolved.get(0), evExp.getDouble(0),
                              5.0e-3, "BroadieKaya: A13 Math.exp drift compounded through Brent on Fourier-CDF")) {
            fail(name + ".evolved[0]: exp=" + evExp.getDouble(0)
                    + " got=" + evolved.get(0)
                    + " Δ=" + Math.abs(evExp.getDouble(0) - evolved.get(0)));
        }
    }

    private static void runCase(final String name) {
        runCase(name, HestonProcess.Discretization.QuadraticExponential);
    }

    private static void runCase(final String name,
                                final HestonProcess.Discretization disc) {
        runFromGroup("processes/hestonprocess_qe", name, disc, true);
    }

    /**
     * NCCV evolve uses an inverse-CDF Brent solver on the non-central
     * chi-squared distribution. Phase 2g WI-1 retry post Brent.solveImpl
     * alignment: attempting TIGHT now that the inverse-CDF Brent matches
     * C++ bit-faithfully.
     */
    private static void runNccvCase(final String name) {
        runFromGroup("processes/hestonprocess_nccv", name,
                HestonProcess.Discretization.NonCentralChiSquareVariance,
                true);
    }

    private static void runFromGroup(final String group, final String name,
                                     final HestonProcess.Discretization disc,
                                     final boolean tight) {
        final ReferenceReader reader = ReferenceReader.load(group);
        final Case c = reader.getCase(name);
        final JSONObject in = c.inputs();
        final double r = in.getDouble("r");
        final double q = in.getDouble("q");
        final double s0 = in.getDouble("s0");
        final double v0 = in.getDouble("v0");
        final double kappa = in.getDouble("kappa");
        final double theta = in.getDouble("theta");
        final double sigma = in.getDouble("sigma");
        final double rho = in.getDouble("rho");
        final double t0 = in.getDouble("t0");
        final double dt = in.getDouble("dt");
        final JSONArray x0a = in.getJSONArray("x0");
        final JSONArray dwa = in.getJSONArray("dw");

        final Date today = new Date(22, Month.April, 2026);
        new Settings().setEvaluationDate(today);

        final YieldTermStructure rCurve = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(r)), new Actual365Fixed());
        final YieldTermStructure qCurve = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(q)), new Actual365Fixed());
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(s0));

        final HestonProcess process = new HestonProcess(
                new Handle<YieldTermStructure>(rCurve),
                new Handle<YieldTermStructure>(qCurve),
                spot, v0, kappa, theta, sigma, rho,
                disc);
        process.update();

        final Array x0 = new Array(new double[] { x0a.getDouble(0), x0a.getDouble(1) });
        final Array dw = new Array(new double[] { dwa.getDouble(0), dwa.getDouble(1) });

        final Array evolved = process.evolve(t0, x0, dt, dw);

        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray evExp = exp.getJSONArray("evolved");
        if (tight) {
            assertDoubleTight(name + ".evolved[0]", evExp.getDouble(0), evolved.get(0));
            assertDoubleTight(name + ".evolved[1]", evExp.getDouble(1), evolved.get(1));
        } else {
            assertDoubleLoose(name + ".evolved[0]", evExp.getDouble(0), evolved.get(0));
            assertDoubleLoose(name + ".evolved[1]", evExp.getDouble(1), evolved.get(1));
        }
    }

    private static void assertDoubleTight(final String label, final double exp, final double got) {
        if (!Tolerance.tight(got, exp)) {
            fail(label + ": exp=" + exp + " got=" + got + " Δ=" + Math.abs(exp - got));
        }
        // Route through assertEquals for the test report's convenience.
        assertEquals(label, exp, got, Math.abs(exp) * 1.0e-12 + 1.0e-14);
    }

    private static void assertDoubleLoose(final String label, final double exp, final double got) {
        if (!Tolerance.loose(got, exp)) {
            fail(label + ": exp=" + exp + " got=" + got + " Δ=" + Math.abs(exp - got));
        }
        assertEquals(label, exp, got, Math.abs(exp) * Tolerance.LOOSE_REL + Tolerance.LOOSE_ABS);
    }
}
