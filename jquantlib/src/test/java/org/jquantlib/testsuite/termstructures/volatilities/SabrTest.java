/*
 Copyright (C) 2007 Richard Gomes

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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.termstructures.volatilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.volatilities.Sabr;
import org.junit.Test;


/**
 * @author <Richard Gomes>
 */
public class SabrTest {

	public SabrTest() {
		QL.info("::::: "+this.getClass().getSimpleName()+" :::::");
	}

	@Test
	public void testAgainstKnownValues() {

		double strike = 0.0398;
        final double forward = 0.0398;
        final double expiryTime = 5.0;
        final double alpha = 0.0305473;
        final double beta = 0.5;
        final double nu = 0.34;
        final double rho = -0.11;

        final Sabr sabr = new Sabr();
        double sabrVol = sabr.sabrVolatility(strike, forward, expiryTime, alpha, beta, nu, rho);
        assertEquals(0.16,sabrVol, 1.0e-6);

        strike = 0.0598;
        sabrVol = sabr.sabrVolatility(strike, forward, expiryTime, alpha, beta, nu, rho);
        assertEquals(0.15755519,sabrVol, 1.0e-6);

        strike = 0.0198;
        sabrVol = sabr.sabrVolatility(strike, forward, expiryTime, alpha, beta, nu, rho);
        assertEquals(0.2373848,sabrVol, 1.0e-6);

	}

    /**
     * Phase 4f.5: regression test that {@code unsafeSabrVolatility} (default
     * dispatcher) matches the explicit lognormal variant.
     */
    @Test
    public void testLognormalDispatcherConsistency() {
        final Sabr sabr = new Sabr();
        final double strike = 0.05, forward = 0.05, expiryTime = 1.5;
        final double alpha = 0.10, beta = 0.5, nu = 0.40, rho = -0.20;
        final double dispatched = sabr.unsafeSabrVolatility(strike, forward, expiryTime,
                alpha, beta, nu, rho);
        final double explicit = sabr.unsafeSabrLogNormalVolatility(strike, forward, expiryTime,
                alpha, beta, nu, rho);
        assertEquals("dispatcher must equal explicit lognormal", explicit, dispatched, 0.0);
    }

    /**
     * Phase 4f.5: shifted SABR with shift=0 must equal unshifted SABR
     * (identity property of the shift transform).
     */
    @Test
    public void testShiftedSabrZeroShiftEqualsUnshifted() {
        final Sabr sabr = new Sabr();
        final double strike = 0.04, forward = 0.05, expiryTime = 2.0;
        final double alpha = 0.15, beta = 0.7, nu = 0.30, rho = -0.10;
        final double shifted = sabr.unsafeShiftedSabrVolatility(strike, forward, expiryTime,
                alpha, beta, nu, rho, 0.0, VolatilityType.ShiftedLognormal);
        final double unshifted = sabr.unsafeSabrLogNormalVolatility(strike, forward, expiryTime,
                alpha, beta, nu, rho);
        assertEquals("shift=0 must reduce to unshifted lognormal SABR",
                unshifted, shifted, 1.0e-14);
    }

    /**
     * Phase 4f.5: validate the {@code VolatilityType}-aware
     * {@code unsafeSabrVolatility} dispatcher.
     */
    @Test
    public void testVolatilityTypeDispatcher() {
        final Sabr sabr = new Sabr();
        final double strike = 0.05, forward = 0.05, expiryTime = 1.0;
        final double alpha = 0.10, beta = 0.5, nu = 0.40, rho = -0.20;

        final double ln = sabr.unsafeSabrVolatility(strike, forward, expiryTime,
                alpha, beta, nu, rho, VolatilityType.ShiftedLognormal);
        final double lnExplicit = sabr.unsafeSabrLogNormalVolatility(strike, forward, expiryTime,
                alpha, beta, nu, rho);
        assertEquals(lnExplicit, ln, 0.0);

        final double normal = sabr.unsafeSabrVolatility(strike, forward, expiryTime,
                alpha, beta, nu, rho, VolatilityType.Normal);
        final double normalExplicit = sabr.unsafeSabrNormalVolatility(strike, forward, expiryTime,
                alpha, beta, nu, rho);
        assertEquals(normalExplicit, normal, 0.0);

        // Lognormal vol and normal vol must both be positive.
        assertTrue("lognormal SABR vol > 0", ln > 0);
        assertTrue("normal SABR vol > 0", normal > 0);
    }

    /**
     * Phase 4f.5: Floc'h-Kennedy at the ATM should be close to the Hagan
     * lognormal value (both pricing the same model). Loose tolerance
     * because the expansions are approximations of one another.
     */
    @Test
    public void testFlochKennedyAtmCloseToHagan() {
        final Sabr sabr = new Sabr();
        final double forward = 0.05, expiryTime = 1.0;
        final double alpha = 0.15, beta = 0.6, nu = 0.30, rho = -0.20;

        // ATM strike: m = F/k = 1.0 → Taylor branch
        final double fkAtm = sabr.sabrFlochKennedyVolatility(forward, forward, expiryTime,
                alpha, beta, nu, rho);
        final double haganAtm = sabr.unsafeSabrLogNormalVolatility(forward, forward, expiryTime,
                alpha, beta, nu, rho);
        assertTrue("Floc'h-Kennedy at ATM finite", Double.isFinite(fkAtm));
        assertTrue("Hagan at ATM finite", Double.isFinite(haganAtm));
        // expansions agree to ~0.5% at ATM for moderate vol of vol
        assertEquals("Floc'h-Kennedy ~ Hagan at ATM", haganAtm, fkAtm, 5e-3);
    }
}
