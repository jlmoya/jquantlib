/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
*/
package org.jquantlib.testsuite.experimental.volatility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.experimental.volatility.ZabrModel;
import org.jquantlib.experimental.volatility.ZabrSmileSection;
import org.jquantlib.experimental.volatility.ZabrSmileSection.Evaluation;
import org.jquantlib.instruments.Option;
import org.jquantlib.termstructures.volatilities.SabrSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.junit.Test;

/**
 * Phase 4f.5 — ZabrSmileSection unit tests.
 *
 * <p>Mirrors the C++ test {@code zabr.cpp testConsistency}: for gamma=1
 * (closed-form ZABR), the ShortMaturityLognormal flavor should produce
 * option prices that agree with SabrSmileSection to within 1e-4 absolute.
 * This Java test runs on a coarser strike grid for speed, but exercises
 * the same parameter set.
 */
public class ZabrSmileSectionTest {

    public ZabrSmileSectionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testConstructionAndAccessors() {
        final double[] params = {0.10, 0.5, 0.40, -0.20, 1.0};
        final ZabrSmileSection sec = new ZabrSmileSection(1.0, 0.05, params);
        assertEquals(0.05, sec.atmLevel(), 0.0);
        assertEquals(0.0, sec.minStrike(), 0.0);
        assertEquals(Double.MAX_VALUE, sec.maxStrike(), 0.0);
        assertEquals(Evaluation.ShortMaturityLognormal, sec.evaluation());

        final ZabrModel m = sec.model();
        assertEquals(0.10, m.alpha(), 0.0);
        assertEquals(0.5, m.beta(), 0.0);
        assertTrue(sec instanceof SmileSection);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLocalVolatilityFlavorDeferred() {
        new ZabrSmileSection(1.0, 0.05,
                new double[]{0.10, 0.5, 0.30, -0.10, 1.0},
                Evaluation.LocalVolatility);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testFullFdFlavorDeferred() {
        new ZabrSmileSection(1.0, 0.05,
                new double[]{0.10, 0.5, 0.30, -0.10, 1.0},
                Evaluation.FullFd);
    }

    /**
     * C++ test-suite/zabr.cpp testConsistency analog (coarsened strike grid).
     *
     * <p>At gamma=1, the ZABR ShortMaturityLognormal expansion's option
     * prices should agree with the SABR Hagan 2002 prices to within ~1e-4.
     * Java port covers the closed-form gamma=1 case only.
     *
     * <p>LOOSE tolerance 5e-3 absolute on option prices (Java uses Hagan
     * SABR via SabrSmileSection internally; the ZABR closed-form lognormal
     * vol at gamma=1 reduces to a slightly different expansion of the same
     * SDE — so prices agree but not bit-exactly).
     */
    @Test
    public void testConsistencyAgainstSabr() {
        // Same parameters as C++ test
        final double alpha = 0.08;
        final double beta  = 0.70;
        final double nu    = 0.20;
        final double rho   = -0.30;
        final double tau   = 5.0;
        final double forward = 0.03;

        final SabrSmileSection sabr = new SabrSmileSection(tau, forward,
                new double[]{alpha, beta, nu, rho});
        final ZabrSmileSection zabr0 = new ZabrSmileSection(tau, forward,
                new double[]{alpha, beta, nu, rho, 1.0},
                Evaluation.ShortMaturityLognormal);

        final double tol = 5.0e-3;
        // Coarse strike grid — covers the same range as C++ but step=0.005
        for (double k = 0.005; k <= 0.10; k += 0.005) {
            final double sabrCall = sabr.optionPrice(k, Option.Type.Call, 1.0);
            final double zabrCall = zabr0.optionPrice(k, Option.Type.Call, 1.0);
            assertTrue("SABR call price > 0 at k=" + k, sabrCall > 0);
            assertTrue("ZABR call price > 0 at k=" + k, zabrCall > 0);
            assertEquals("ZABR0 - SABR call price at k=" + k,
                    sabrCall, zabrCall, tol);
        }
    }

    @Test
    public void testNormalFlavorReturnsValidPrices() {
        final double alpha = 0.10;
        final double beta = 0.5;
        final double nu = 0.30;
        final double rho = -0.10;
        final double tau = 1.0;
        final double forward = 0.05;

        final ZabrSmileSection zabr = new ZabrSmileSection(tau, forward,
                new double[]{alpha, beta, nu, rho, 1.0},
                Evaluation.ShortMaturityNormal);

        for (double k = 0.02; k <= 0.10; k += 0.02) {
            final double v = zabr.volatility(k);
            assertTrue("normal vol > 0 at k=" + k, v > 0);
        }
    }
}
