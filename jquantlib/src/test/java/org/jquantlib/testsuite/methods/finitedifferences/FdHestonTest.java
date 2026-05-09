/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences;

import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.methods.finitedifferences.meshers.FdmHestonVarianceMesher;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5j port of {@code test-suite/fdheston.cpp} v1.42.1.
 *
 * <p>Currently only {@code testFdmHestonVarianceMesher} is implemented — the
 * remaining 8 cases (Barrier, Barrier-vs-BS, American, Ikonen-Toivanen,
 * BlackScholes, EuropeanWithDividends, Convergence, Intraday) require
 * {@code FdHestonVanillaEngine} and {@code FdHestonBarrierEngine} which are
 * NOT yet ported (Phase 4n.5 carry-forward — see Phase 5j.5 plan).
 *
 * <p><strong>Tolerance tier</strong>: TIGHT 1e-6 absolute for variance-mesh
 * locations (matches C++ tolerance verbatim).  The test reproduces the
 * fixed mesh-point reference values from C++ {@code test-suite/fdheston.cpp}
 * lines 121-123 — these are stable under identical inputs and identical
 * non-central chi-squared distribution implementation.
 */
public class FdHestonTest {

    /** {@code testFdmHestonVarianceMesher} (partial — variance-mesh only).
     * The C++ test additionally exercises
     * {@code FdmHestonLocalVolatilityVarianceMesher}; that class is NOT
     * ported yet (Phase 5j.5 carry).  Variance-mesh portion is faithful.
     */
    @Test
    public void testFdmHestonVarianceMesher() {
        final Date today = new Date(22, Month.February, 2018);
        final DayCounter dc = new Actual365Fixed();
        new Settings().setEvaluationDate(today);

        final Handle<Quote> rateQ     = new Handle<Quote>(new SimpleQuote(0.02));
        final Handle<Quote> dividendQ = new Handle<Quote>(new SimpleQuote(0.02));
        final YieldTermStructure r = new FlatForward(today, rateQ, dc,
                Compounding.Continuous, Frequency.Annual);
        final YieldTermStructure q = new FlatForward(today, dividendQ, dc,
                Compounding.Continuous, Frequency.Annual);

        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(100.0));
        final HestonProcess process = new HestonProcess(
                new Handle<YieldTermStructure>(r),
                new Handle<YieldTermStructure>(q),
                spot,
                0.09,   // v0
                1.0,    // kappa
                0.09,   // theta
                0.2,    // sigma
                -0.5);  // rho

        final FdmHestonVarianceMesher mesher = new FdmHestonVarianceMesher(
                5, process, 1.0);

        // Reference values from test-suite/fdheston.cpp lines 121-123
        final double[] expected = {
                0.0,
                6.652314e-02,
                9.000000e-02,
                1.095781e-01,
                2.563610e-01
        };

        // C++ tol = 1e-6 absolute
        final double tol = 1e-6;

        for (int i = 0; i < expected.length; ++i) {
            final double got = mesher.locations()[i];
            final double diff = Math.abs(expected[i] - got);
            if (diff > tol) {
                fail("FdmHestonVarianceMesher location[" + i + "] mismatch:"
                        + "\n  expected:   " + expected[i]
                        + "\n  calculated: " + got
                        + "\n  diff:       " + diff
                        + "\n  tol:        " + tol);
            }
        }
    }

    // ------------------------------------------------------------------------
    // ----------------- DEFERRED — Phase 5j.5 carry-forward -----------------
    // ------------------------------------------------------------------------

    /** {@code testFdmHestonBarrierVsBlackScholes} — requires
     * {@code FdHestonBarrierEngine} + {@code FdBlackScholesBarrierEngine}.
     * Java has neither; Phase 4n.5 carry-forward.
     */
    @Ignore("Phase 5j.5 — requires FdHestonBarrierEngine (Phase 4n.5 carry)")
    @Test
    public void testFdmHestonBarrierVsBlackScholes() {
        fail("not implemented");
    }

    @Ignore("Phase 5j.5 — requires FdHestonBarrierEngine (Phase 4n.5 carry)")
    @Test
    public void testFdmHestonBarrier() {
        fail("not implemented");
    }

    @Ignore("Phase 5j.5 — requires FdHestonVanillaEngine + FdmAmericanStepCondition wiring")
    @Test
    public void testFdmHestonAmerican() {
        fail("not implemented");
    }

    /** {@code testFdmHestonIkonenToivanen} — Ikonen-Toivanen splitting
     * scheme for American Heston.  Needs FdHestonVanillaEngine + IT scheme
     * wiring.
     */
    @Ignore("Phase 5j.5 — requires FdHestonVanillaEngine + Ikonen-Toivanen scheme")
    @Test
    public void testFdmHestonIkonenToivanen() {
        fail("not implemented");
    }

    /** {@code testFdmHestonBlackScholes} — degenerate-vol Heston should
     * collapse to BS.  Needs FdHestonVanillaEngine.
     */
    @Ignore("Phase 5j.5 — requires FdHestonVanillaEngine (Phase 4n.5 carry)")
    @Test
    public void testFdmHestonBlackScholes() {
        fail("not implemented");
    }

    @Ignore("Phase 5j.5 — requires FdHestonVanillaEngine + FdmDividendHandler integration")
    @Test
    public void testFdmHestonEuropeanWithDividends() {
        fail("not implemented");
    }

    @Ignore("Phase 5j.5 — requires FdHestonVanillaEngine convergence regression suite")
    @Test
    public void testFdmHestonConvergence() {
        fail("not implemented");
    }

    @Ignore("Phase 5j.5 — requires FdHestonVanillaEngine + intraday-clock integration")
    @Test
    public void testFdmHestonIntradayPricing() {
        fail("not implemented");
    }

    @Ignore("Phase 5j.5 — requires FdHestonVanillaEngine + Method-of-Lines and Crank-Nicolson timing")
    @Test
    public void testMethodOfLinesAndCN() {
        fail("not implemented");
    }

    @Ignore("Phase 5j.5 — requires FdHestonVanillaEngine + spurious-oscillation regression baseline")
    @Test
    public void testSpuriousOscillations() {
        fail("not implemented");
    }

    @Ignore("Phase 5j.5 — requires FdHestonVanillaEngine + American-call-put parity check")
    @Test
    public void testAmericanCallPutParity() {
        fail("not implemented");
    }
}
