/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.volatility;

import org.jquantlib.experimental.volatility.NoArbSabrModel;
import org.jquantlib.experimental.volatility.NoArbSabrSmileSection;
import org.jquantlib.experimental.volatility.ZabrModel;
import org.jquantlib.termstructures.volatilities.Sabr;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Phase 4f smoke tests for the ZABR/NoArbSABR scaffolding.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>{@link ZabrModel} constructor validates SABR + gamma bounds.</li>
 *   <li>{@link NoArbSabrModel} constructor validates Doust 2012 bounds.</li>
 *   <li>{@link NoArbSabrSmileSection#volatilityImpl(double)} falls back to
 *       the Hagan SABR closed form when the deferred {@code optionPrice}
 *       throws (which it always does in the Phase 4f scaffold).</li>
 * </ul>
 *
 * <p>Pricing/density methods are deliberately not tested — they throw
 * {@link UnsupportedOperationException} until Phase 4f.5.
 */
public class ZabrAndNoArbSabrSmokeTest {

    private static final double TOL = 1e-12;

    @Test
    public void testZabrModelConstructor() {
        final ZabrModel m = new ZabrModel(1.0, 100.0, 0.10, 0.5, 0.40, -0.20, 0.5);
        assertEquals(1.0, m.expiryTime(), 0.0);
        assertEquals(100.0, m.forward(), 0.0);
        assertEquals(0.10, m.alpha(), 0.0);
        assertEquals(0.5, m.beta(), 0.0);
        // nu_ stored is the C++ transformation: nu * alpha^(1-gamma)
        assertEquals(0.40 * Math.pow(0.10, 0.5), m.nu(), TOL);
        assertEquals(-0.20, m.rho(), 0.0);
        assertEquals(0.5, m.gamma(), 0.0);
    }

    @Test(expected = Exception.class)
    public void testZabrModelRejectsNegativeGamma() {
        new ZabrModel(1.0, 100.0, 0.10, 0.5, 0.40, -0.20, -0.1);
    }

    @Test
    public void testNoArbSabrModelConstructor() {
        // forward=0.05, beta=0.5, alpha picked so sigmaI is in (0.05, 1.0)
        // sigmaI = alpha * forward^(beta-1) = alpha * 0.05^(-0.5) ≈ alpha * 4.472
        // pick alpha = 0.05  ⇒ sigmaI ≈ 0.224 ∈ [0.05, 1.0] OK
        final NoArbSabrModel m = new NoArbSabrModel(1.0, 0.05, 0.05, 0.5, 0.30, -0.30);
        assertEquals(1.0, m.expiryTime(), 0.0);
        assertEquals(0.05, m.forward(), 0.0);
        assertEquals(0.05, m.alpha(), 0.0);
    }

    @Test(expected = Exception.class)
    public void testNoArbSabrModelRejectsBetaOutOfBounds() {
        new NoArbSabrModel(1.0, 0.05, 0.05, 0.999, 0.30, -0.30);
    }

    @Test
    public void testNoArbSabrSmileSectionHaganFallback() {
        // alpha, beta, nu, rho selected so unsafeSabrVolatility succeeds.
        final double[] params = {0.05, 0.5, 0.30, -0.30};
        final NoArbSabrSmileSection s = new NoArbSabrSmileSection(1.0, 0.05, params);

        // Hagan fallback should reproduce direct Sabr formula at strike == forward.
        final double vol = s.volatility(0.05);

        final double expected = new Sabr().unsafeSabrVolatility(
                0.05, 0.05, 1.0, params[0], params[1], params[2], params[3]);

        assertEquals("Hagan fallback should match direct SABR formula at ATM",
                expected, vol, TOL);
        assertTrue("Volatility should be positive", vol > 0.0);
    }
}
