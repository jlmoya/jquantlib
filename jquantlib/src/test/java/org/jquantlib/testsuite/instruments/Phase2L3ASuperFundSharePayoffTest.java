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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jquantlib.instruments.SuperFundPayoff;
import org.jquantlib.instruments.SuperSharePayoff;
import org.junit.Test;

/**
 * Smoke tests for {@link SuperFundPayoff} / {@link SuperSharePayoff}.
 *
 * <p>Expected values follow directly from the v1.42.1 payoff formulas
 * ({@code ql/instruments/payoffs.cpp:197-224}).
 *
 * <p>Tolerance: exact (1e-14 relative) — pure arithmetic comparisons.
 *
 * @author JQuantLib migration team (Phase 2 L3-A)
 */
public class Phase2L3ASuperFundSharePayoffTest {

    private static final double TOL = 1.0e-14;

    @Test
    public void testSuperFundPayoff() {
        final SuperFundPayoff p = new SuperFundPayoff(100.0, 110.0);
        assertEquals("SuperFund", p.name());

        // price below first strike → 0
        assertEquals(0.0, p.get(99.999), TOL);

        // price at first strike → 100/100=1
        assertEquals(1.0, p.get(100.0), TOL);

        // price between strikes → price/strike
        assertEquals(105.0 / 100.0, p.get(105.0), TOL);

        // price at second strike → 0 (half-open interval)
        assertEquals(0.0, p.get(110.0), TOL);

        // price above second strike → 0
        assertEquals(0.0, p.get(120.0), TOL);
    }

    @Test
    public void testSuperFundStrikeOrderingRequirement() {
        try {
            new SuperFundPayoff(100.0, 90.0);
            fail("expected exception: second strike must be > first strike");
        } catch (final RuntimeException expected) {
            // OK
        }
        try {
            new SuperFundPayoff(0.0, 110.0);
            fail("expected exception: strike must be > 0");
        } catch (final RuntimeException expected) {
            // OK
        }
    }

    @Test
    public void testSuperSharePayoff() {
        final SuperSharePayoff p = new SuperSharePayoff(100.0, 110.0, 5.0);
        assertEquals("SuperShare", p.name());
        assertEquals(100.0, p.strike(), TOL);
        assertEquals(110.0, p.secondStrike(), TOL);
        assertEquals(5.0, p.cashPayoff(), TOL);

        // price below first strike → 0
        assertEquals(0.0, p.get(50.0), TOL);

        // price within [first, second) → cashPayoff
        assertEquals(5.0, p.get(100.0), TOL);
        assertEquals(5.0, p.get(105.0), TOL);

        // price at second strike → 0 (half-open interval)
        assertEquals(0.0, p.get(110.0), TOL);

        // price above second strike → 0
        assertEquals(0.0, p.get(150.0), TOL);
    }

    @Test
    public void testSuperShareStrikeOrderingRequirement() {
        try {
            new SuperSharePayoff(100.0, 90.0, 1.0);
            fail("expected exception: second strike must be > first strike");
        } catch (final RuntimeException expected) {
            // OK
        }
    }
}
