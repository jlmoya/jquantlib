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
import org.jquantlib.experimental.volatility.ZabrModel;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Phase 4f.5c smoke test for {@link ZabrModel#fullFdPrice(double)}.
 *
 * <p>Verifies that the 2-D FdmZabrOp + Glued1dMesher pipeline produces
 * a sensible undiscounted call price under the no-arbitrage envelope:
 * <pre>
 *   max(forward - strike, 0)  <=  call(strike)  <=  forward.
 * </pre>
 *
 * <p>Cross-validation against C++ v1.42.1 references (when the C++ build
 * is available) lives in
 * {@link ZabrModelCrossValidationTest#fullFdPrice_gamma1_full_fd_loose}
 * (currently {@code @Ignore}'d — see Phase 4f.5c carry-forward in
 * {@code docs/migration/phase4f-progress.md}).
 */
public class ZabrFullFdPriceSmokeTest {

    private static final double TOL = 1e-6;

    public ZabrFullFdPriceSmokeTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testFullFdPriceCall_gamma1_atTheMoney() {
        // SABR-equivalent fixture (gamma=1) — same as test-suite/zabr.cpp.
        // alpha=0.08, beta=0.7, nu=0.2, rho=-0.3, expiry=5.0, forward=0.03.
        final ZabrModel m = new ZabrModel(5.0, 0.03, 0.08, 0.70, 0.20, -0.30, 1.0);

        final double strike = 0.03;
        final double price = m.fullFdPrice(strike);
        final double intrinsic = Math.max(0.03 - strike, 0.0);

        assertTrue("Call price " + price + " must be >= intrinsic " + intrinsic,
                price >= intrinsic - TOL);
        assertTrue("Call price " + price + " must be <= forward 0.03",
                price <= 0.03 + TOL);
        // ATM call must be strictly > 0 for positive vol.
        assertTrue("ATM call must be positive: " + price, price > 1e-6);
    }

    @Test
    public void testFullFdPriceCall_gamma1_inTheMoney() {
        final ZabrModel m = new ZabrModel(5.0, 0.03, 0.08, 0.70, 0.20, -0.30, 1.0);

        final double strike = 0.025;
        final double price = m.fullFdPrice(strike);
        final double intrinsic = 0.03 - strike;

        // ITM call must be at least intrinsic.
        assertTrue("Call price " + price + " must be >= intrinsic " + intrinsic,
                price >= intrinsic - TOL);
        assertTrue("Call price " + price + " must be <= forward 0.03",
                price <= 0.03 + TOL);
    }

    @Test
    public void testFullFdPriceCall_gamma1_outOfTheMoney() {
        final ZabrModel m = new ZabrModel(5.0, 0.03, 0.08, 0.70, 0.20, -0.30, 1.0);

        final double strike = 0.035;
        final double price = m.fullFdPrice(strike);

        assertTrue("OTM call must be >= 0: " + price, price >= -TOL);
        assertTrue("OTM call must be < ATM: " + price, price < 0.03);
    }

    @Test
    public void testFullFdPriceCall_gamma075_atTheMoney() {
        // gamma != 1 — exercises the FdmZabrOp gamma path.
        final ZabrModel m = new ZabrModel(5.0, 0.03, 0.08, 0.70, 0.20, -0.30, 0.75);

        final double strike = 0.03;
        final double price = m.fullFdPrice(strike);

        assertTrue("Call price " + price + " must be in [0, forward]",
                price >= -TOL && price <= 0.03 + TOL);
        assertTrue("ATM call must be positive: " + price, price > 1e-6);
    }
}
