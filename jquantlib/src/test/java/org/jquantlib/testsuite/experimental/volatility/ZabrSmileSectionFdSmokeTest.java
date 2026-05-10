/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.
 */
package org.jquantlib.testsuite.experimental.volatility;

import org.jquantlib.QL;
import org.jquantlib.experimental.volatility.ZabrSmileSection;
import org.jquantlib.experimental.volatility.ZabrSmileSection.Evaluation;
import org.jquantlib.instruments.Option;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Phase 4f.5c smoke test for {@link ZabrSmileSection} with the
 * {@link Evaluation#LocalVolatility} and {@link Evaluation#FullFd} flavors.
 *
 * <p>Verifies the call price envelope:
 * <pre>
 *   max(forward - strike, 0) <= callPrice(strike) <= forward
 * </pre>
 * across a small set of strikes for each flavor, and that
 * {@link ZabrSmileSection#optionPrice(double, Option.Type)} accepts strikes
 * both inside the FD grid (interpolation path) and outside the right end
 * (exponential extrapolation path).
 *
 * <p>Cross-validation against C++ v1.42.1 references for
 * {@code ZabrSmileSection<ZabrLocalVolatility>} /
 * {@code ZabrSmileSection<ZabrFullFd>} is carry-forward to a session with
 * the C++ harness build available — see Phase 4f.5d note in
 * {@code docs/migration/phase4f-progress.md}.
 *
 * <p>Uses a small moneyness grid + low fdRefinement to keep runtime
 * acceptable: each {@link Evaluation#FullFd} strike triggers a 100×100
 * Hundsdorfer rollback (~few seconds per strike), so we limit the FullFd
 * fixture to 3-4 strikes.
 */
public class ZabrSmileSectionFdSmokeTest {

    private static final double TOL = 1e-6;

    public ZabrSmileSectionFdSmokeTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testLocalVolatilityFlavor_callPriceEnvelope() {
        // SABR-equivalent fixture (gamma=1) — same as test-suite/zabr.cpp.
        // alpha=0.08, beta=0.7, nu=0.2, rho=-0.3, expiry=5.0, forward=0.03.
        // Use compact moneyness + low fdRefinement to keep runtime <60s.
        final double[] zabr = {0.08, 0.70, 0.20, -0.30, 1.0};
        final double[] moneyness = {0.5, 1.0, 1.5};
        final ZabrSmileSection s = new ZabrSmileSection(
                5.0, 0.03, zabr, Evaluation.LocalVolatility, moneyness, 1);

        for (final double strike : new double[] {0.020, 0.030, 0.040, 0.060}) {
            final double call = s.optionPrice(strike, Option.Type.Call);
            final double intrinsic = Math.max(0.03 - strike, 0.0);
            assertTrue("call(" + strike + ")=" + call
                    + " must be >= intrinsic " + intrinsic,
                    call >= intrinsic - TOL);
            assertTrue("call(" + strike + ")=" + call + " must be <= forward 0.03",
                    call <= 0.03 + TOL);
        }
    }

    @Test
    public void testFullFdFlavor_callPriceEnvelope_atTheMoney() {
        // Use 2 moneyness ticks + fdRefinement=2 to keep FullFd cheap:
        // 2 ticks × forward=0.03 → strikes {0.015, 0.045}; with refinement=2
        // we insert 2 sub-strikes between → 4 strikes total. After init3
        // prepend (0, fwd) we have 5 nodes — enough for cubic spline.
        // Each FullFd evaluation builds a 100x100 mesh → ~few seconds × 4.
        final double[] zabr = {0.08, 0.70, 0.20, -0.30, 1.0};
        final double[] moneyness = {0.5, 1.5};
        final ZabrSmileSection s = new ZabrSmileSection(
                5.0, 0.03, zabr, Evaluation.FullFd, moneyness, 2);

        final double call = s.optionPrice(0.03, Option.Type.Call);
        assertTrue("ATM call must be in (0, forward): " + call,
                call > 1.0e-6 && call <= 0.03 + TOL);
    }

    @Test
    public void testLocalVolatilityFlavor_putCallParity() {
        final double[] zabr = {0.08, 0.70, 0.20, -0.30, 1.0};
        final double[] moneyness = {0.5, 1.0, 1.5};
        final ZabrSmileSection s = new ZabrSmileSection(
                5.0, 0.03, zabr, Evaluation.LocalVolatility, moneyness, 1);

        // Put-Call parity (undiscounted): C - P = F - K
        for (final double strike : new double[] {0.020, 0.030, 0.040}) {
            final double call = s.optionPrice(strike, Option.Type.Call);
            final double put = s.optionPrice(strike, Option.Type.Put);
            final double parity = (0.03 - strike);
            assertTrue("Put-call parity violated at strike " + strike
                    + ": C=" + call + " P=" + put + " F-K=" + parity
                    + " diff=" + Math.abs(call - put - parity),
                    Math.abs(call - put - parity) < 1.0e-9);
        }
    }
}
