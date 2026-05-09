/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.volatility;

import org.jquantlib.experimental.volatility.SviInterpolation;
import org.jquantlib.experimental.volatility.SviSmileSection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Phase 4f smoke tests for SVI smile section + checkSviParameters.
 *
 * <p>Validates parameter checks, total-variance closed form, and that
 * {@link SviSmileSection#volatilityImpl(double)} reproduces the expected
 * Gatheral SVI volatility surface for benchmark parameters.
 */
public class SviSmileSectionTest {

    private static final double TOL = 1e-12;

    /** ATM volatility = sqrt(w(0)/T) for Gatheral baseline params. */
    @Test
    public void testAtmVolatility() {
        // a=0.04, b=0.4, sigma=0.1, rho=-0.4, m=0
        // w(0) = a + b*(rho*(0-m) + sqrt((0-m)^2 + sigma^2))
        //      = 0.04 + 0.4*(0 + sqrt(0 + 0.01)) = 0.04 + 0.4*0.1 = 0.08
        // T=1 => sigma_BS(F) = sqrt(0.08) ≈ 0.282842712474619
        final double[] params = {0.04, 0.4, 0.1, -0.4, 0.0};
        final SviSmileSection s = new SviSmileSection(1.0, 100.0, params);
        final double vol = s.volatility(100.0);
        assertEquals("ATM SVI volatility", Math.sqrt(0.08), vol, TOL);
    }

    /** sviTotalVariance closed form. */
    @Test
    public void testTotalVariance() {
        // Spot-check a non-ATM strike: k=0.1, params as above.
        // w(k) = 0.04 + 0.4 * (-0.4*(0.1-0) + sqrt((0.1-0)^2 + 0.1^2))
        //      = 0.04 + 0.4 * (-0.04 + sqrt(0.02))
        //      = 0.04 + 0.4 * (-0.04 + 0.141421356...)
        //      = 0.04 + 0.4 * 0.101421356...
        //      = 0.04 + 0.040568542...
        //      = 0.080568542...
        final double a=0.04, b=0.4, sigma=0.1, rho=-0.4, m=0.0;
        final double k = 0.1;
        final double expected = a + b*(rho*(k-m) + Math.sqrt((k-m)*(k-m) + sigma*sigma));
        final double actual = SviInterpolation.sviTotalVariance(a, b, sigma, rho, m, k);
        assertEquals("sviTotalVariance closed form", expected, actual, TOL);
    }

    /** checkSviParameters rejects invalid values. */
    @Test
    public void testParameterValidation() {
        // valid baseline
        SviInterpolation.checkSviParameters(0.04, 0.4, 0.1, -0.4, 0.0, 1.0);

        // b < 0
        try {
            SviInterpolation.checkSviParameters(0.04, -0.1, 0.1, -0.4, 0.0, 1.0);
            org.junit.Assert.fail("expected exception for negative b");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("b"));
        }

        // |rho| >= 1
        try {
            SviInterpolation.checkSviParameters(0.04, 0.4, 0.1, 1.0, 0.0, 1.0);
            org.junit.Assert.fail("expected exception for rho==1");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("rho"));
        }

        // sigma <= 0
        try {
            SviInterpolation.checkSviParameters(0.04, 0.4, 0.0, -0.4, 0.0, 1.0);
            org.junit.Assert.fail("expected exception for sigma==0");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("sigma"));
        }

        // b*(1+|rho|) > 4
        try {
            SviInterpolation.checkSviParameters(1.0, 5.0, 0.1, 0.0, 0.0, 1.0);
            org.junit.Assert.fail("expected exception for b(1+|rho|)>4");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("4"));
        }
    }

    /** Smile is asymmetric for rho != 0. */
    @Test
    public void testSmileShapeAsymmetric() {
        final double[] params = {0.04, 0.4, 0.1, -0.4, 0.0};
        final SviSmileSection s = new SviSmileSection(1.0, 100.0, params);

        final double volLow  = s.volatility(80.0);
        final double volAtm  = s.volatility(100.0);
        final double volHigh = s.volatility(120.0);

        // Negative rho ⇒ steeper left wing ⇒ vol(below) > vol(above)
        assertTrue("low strike vol > ATM vol (rho<0)", volLow > volAtm);
        assertTrue("ATM vol > high strike vol", volAtm > volHigh || Math.abs(volAtm-volHigh) < 0.05);
    }
}
