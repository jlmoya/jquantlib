/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.processes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.processes.HybridHestonHullWhiteProcess;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.processes.HullWhiteForwardProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * Phase 5h.5-HHW WI-1 smoke tests for
 * {@link HybridHestonHullWhiteProcess}. Exercises the structural
 * arithmetic (initial values, dimensions, drift, diffusion, numeraire
 * round-trip at t=0) without yet running a full Monte Carlo, which is
 * deferred until the {@code MCHestonHullWhiteEngine} infrastructure is
 * ported (Phase 5h.5-HHW-MC).
 *
 * <p>The full 13-case cross-validation against C++ lives in
 * {@link HybridHestonHullWhiteProcessTest} (still {@code @Ignore}'d
 * pending those engine ports).
 */
public class HybridHestonHullWhiteProcessSmokeTest {

    @Test
    public void testDimensionsAndInitialValues() {
        final Setup s = setupBaseline();
        assertEquals("size", 3, s.process.size());
        assertEquals("factors", 3, s.process.factors());
        final Array x0 = s.process.initialValues();
        assertEquals("x0 length", 3, x0.size());
        assertEquals("S0", 100.0, x0.get(0), 1e-15);
        assertEquals("v0", 0.04, x0.get(1), 1e-15);
        // r0 derived from flat-forward 0.05 continuous
        assertEquals("r0", 0.05, x0.get(2), 1e-12);
    }

    @Test
    public void testDriftStructure() {
        final Setup s = setupBaseline();
        final Array x = s.process.initialValues();
        final Array d = s.process.drift(0.5, x);
        assertEquals("drift length", 3, d.size());
        // Heston log-S drift = r - q - 0.5*v0 = 0.05 - 0.02 - 0.5*0.04 = 0.01
        assertEquals("S drift", 0.01, d.get(0), 1e-10);
        // Heston variance drift = kappa*(theta - v0) = 1.0*(0.04 - 0.04) = 0
        assertEquals("v drift", 0.0, d.get(1), 1e-10);
        // r drift > 0 typically for HW-forward (positive theta(t))
        assertTrue("r drift finite", Double.isFinite(d.get(2)));
    }

    @Test
    public void testDiffusionRowsHaveCorrectStructure() {
        final Setup s = setupBaseline();
        final Array x = s.process.initialValues();
        final Matrix m = s.process.diffusion(0.5, x);
        assertEquals("diff rows", 3, m.rows());
        assertEquals("diff cols", 3, m.columns());
        // S row: (sqrt(v), 0, 0) — only first nonzero
        assertEquals("[0][1]", 0.0, m.get(0, 1), 1e-15);
        assertEquals("[0][2]", 0.0, m.get(0, 2), 1e-15);
        // v row: (rho*sigma*sqrt(v), sqrt(1-rho^2)*sigma*sqrt(v), 0) — last col zero
        assertEquals("[1][2]", 0.0, m.get(1, 2), 1e-15);
        // r row sum-of-squares should equal sigma_HW^2 (Cholesky condition)
        final double r0sq = m.get(2, 0) * m.get(2, 0)
                          + m.get(2, 1) * m.get(2, 1)
                          + m.get(2, 2) * m.get(2, 2);
        final double sigmaHWsq = 0.01 * 0.01;
        assertEquals("HW row L2 norm = sigma_HW", sigmaHWsq, r0sq, 1e-12);
    }

    @Test
    public void testNumeraireAtZeroEqualsOne() {
        final Setup s = setupBaseline();
        // At t=0 with x[2]=r0, P(0,T)/P(0,T) = 1.
        final Array x = s.process.initialValues();
        assertEquals("numeraire at t=0", 1.0, s.process.numeraire(0.0, x), 1e-12);
    }

    @Test
    public void testEtaAccessor() {
        final Setup s = setupBaseline();
        assertEquals("eta = corrEquityShortRate", -0.3, s.process.eta(), 1e-15);
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static final class Setup {
        HybridHestonHullWhiteProcess process;
        HestonProcess heston;
        HullWhiteForwardProcess hw;
    }

    private Setup setupBaseline() {
        final Setup s = new Setup();
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.July, 2026);
        new Settings().setEvaluationDate(today);

        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.05, dc));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.02, dc));
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(100.0));

        s.heston = new HestonProcess(rTS, qTS, spot,
                0.04, 1.0, 0.04, 0.4, -0.7);
        s.heston.update(); // Java HestonProcess caches scalars in update();
                           // construction alone leaves the cache zero, which
                           // would NaN-poison HHW.diffusion() — see HestonProcess.java.
        s.hw = new HullWhiteForwardProcess(rTS, 0.05, 0.01);
        s.hw.setForwardMeasureTime(10.0);
        s.process = new HybridHestonHullWhiteProcess(s.heston, s.hw, -0.3,
                HybridHestonHullWhiteProcess.Discretization.BSMHullWhite);
        return s;
    }
}
