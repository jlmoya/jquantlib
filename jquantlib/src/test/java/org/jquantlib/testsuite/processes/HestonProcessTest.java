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

    private static void runCase(final String name) {
        runCase(name, HestonProcess.Discretization.QuadraticExponential);
    }

    private static void runCase(final String name,
                                final HestonProcess.Discretization disc) {
        final ReferenceReader reader = ReferenceReader.load("processes/hestonprocess_qe");
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
        assertDoubleTight(name + ".evolved[0]", evExp.getDouble(0), evolved.get(0));
        assertDoubleTight(name + ".evolved[1]", evExp.getDouble(1), evolved.get(1));
    }

    private static void assertDoubleTight(final String label, final double exp, final double got) {
        if (!Tolerance.tight(got, exp)) {
            fail(label + ": exp=" + exp + " got=" + got + " Δ=" + Math.abs(exp - got));
        }
        // Route through assertEquals for the test report's convenience.
        assertEquals(label, exp, got, Math.abs(exp) * 1.0e-12 + 1.0e-14);
    }
}
