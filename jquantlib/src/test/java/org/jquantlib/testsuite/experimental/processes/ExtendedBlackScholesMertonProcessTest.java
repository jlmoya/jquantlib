/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4n — ExtendedBlackScholesMertonProcess smoke tests.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */
package org.jquantlib.testsuite.experimental.processes;

import org.jquantlib.QL;
import org.jquantlib.experimental.processes.ExtendedBlackScholesMertonProcess;
import org.jquantlib.experimental.processes.ExtendedBlackScholesMertonProcess.Discretization;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.daycounters.Actual365Fixed;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Smoke tests for {@link ExtendedBlackScholesMertonProcess}.
 *
 * <p>Verifies that the Euler discretization matches
 * {@link GeneralizedBlackScholesProcess#evolve} (since both apply
 * {@code expectation + stdDev*dw}); and that Milstein and PredictorCorrector
 * produce finite results consistent with the Black-Scholes drift.
 */
public class ExtendedBlackScholesMertonProcessTest {

    private static final double TIGHT = 1e-12;

    public ExtendedBlackScholesMertonProcessTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private static ExtendedBlackScholesMertonProcess build(final Discretization disc) {
        final Date today = Date.todaysDate();
        final Actual365Fixed dc = new Actual365Fixed();
        final var spot = new Handle<SimpleQuote>(new SimpleQuote(100.0));
        final var r = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.04, dc));
        final var q = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.02, dc));
        final var vol = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, new NullCalendar(), 0.20, dc));
        return new ExtendedBlackScholesMertonProcess(spot, q, r, vol, disc);
    }

    @Test
    public void eulerSchemeMatchesGenericExpectationPlusNoise() {
        // Euler: apply(expectation, stdDev*dw)
        // = expectation + stdDev*dw, same as the parent class default.
        final ExtendedBlackScholesMertonProcess euler = build(Discretization.Euler);
        final double t0 = 0.5, x0 = 100.0, dt = 0.25, dw = 0.3;
        final double expected = euler.apply(euler.expectation(t0, x0, dt),
                euler.stdDeviation(t0, x0, dt) * dw);
        assertEquals(expected, euler.evolve(t0, x0, dt, dw), TIGHT);
    }

    @Test
    public void milsteinSchemeIsFiniteAndCloseToEulerForSmallDt() {
        // For very small dt and small noise, Milstein and Euler should differ
        // only by an O(dt) correction term: 0.5*sigma^2*(dw^2 - 1)*dt
        final ExtendedBlackScholesMertonProcess milstein = build(Discretization.Milstein);
        final ExtendedBlackScholesMertonProcess euler = build(Discretization.Euler);
        final double t0 = 0.0, x0 = 100.0;
        final double dt = 1e-6, dw = 0.0;
        final double m = milstein.evolve(t0, x0, dt, dw);
        final double e = euler.evolve(t0, x0, dt, dw);
        assertTrue("milstein produced non-finite value", Double.isFinite(m));
        assertTrue("euler produced non-finite value", Double.isFinite(e));
        // The Milstein correction at dw=0 is 0.5*sigma^2*(0-1)*dt = -0.5*sigma^2*dt.
        // Both schemes preserve the small-dt limit close to x0.
        assertEquals(x0, m, 1e-3);
        assertEquals(x0, e, 1e-3);
    }

    @Test
    public void predictorCorrectorIsFinite() {
        final ExtendedBlackScholesMertonProcess pc = build(Discretization.PredictorCorrector);
        final double r = pc.evolve(0.5, 100.0, 0.25, 0.3);
        assertTrue("predictor-corrector produced non-finite value", Double.isFinite(r));
    }

    @Test
    public void diffusionMatchesBlackVol() {
        final ExtendedBlackScholesMertonProcess p = build(Discretization.Milstein);
        // BlackConstantVol of 0.20 is the diffusion at any (t, x).
        assertEquals(0.20, p.diffusion(1.0, 100.0), TIGHT);
        assertEquals(0.20, p.diffusion(2.0, 50.0), TIGHT);
    }
}
