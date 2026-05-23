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

import org.jquantlib.instruments.Bond;
import org.jquantlib.instruments.FaceValueAccrualClaim;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Smoke test for {@link FaceValueAccrualClaim}.
 *
 * <p>Tolerance: tight ({@code 1e-12}) — closed-form arithmetic only.
 *
 * @author JQuantLib migration team (Phase 2 L3-A)
 */
public class Phase2L3AFaceValueAccrualClaimTest {

    private static final double TOL = 1.0e-12;

    /** Bond stub that returns deterministic accrued/notional, isolating the FaceValueAccrualClaim arithmetic. */
    static class StubBond extends Bond {
        private final double accrued;
        private final double notional;

        StubBond(final double accrued, final double notional) {
            super(0, new NullCalendar(), new Date());
            this.accrued = accrued;
            this.notional = notional;
        }

        @Override
        public double accruedAmount(final Date settlement) {
            return accrued;
        }

        @Override
        public double notional(final Date date) {
            return notional;
        }
    }

    @Test
    public void testFaceValueAccrualClaimFormula() {
        // accrued = 2.5 on notional of 100 → accrual fraction = 0.025
        // recoveryRate = 0.40 → payment = notional_param * (1 - 0.40 - 0.025) = notional_param * 0.575
        final StubBond ref = new StubBond(2.5, 100.0);
        final FaceValueAccrualClaim claim = new FaceValueAccrualClaim(ref);

        final double payout = claim.amount(new Date(), 10_000_000.0, 0.40);
        assertEquals(10_000_000.0 * 0.575, payout, TOL);
    }

    @Test
    public void testFaceValueAccrualClaimNoAccrual() {
        final StubBond ref = new StubBond(0.0, 100.0);
        final FaceValueAccrualClaim claim = new FaceValueAccrualClaim(ref);

        // With no accrual, reduces to FaceValueClaim formula: notional * (1 - recovery)
        assertEquals(1_000_000.0 * (1.0 - 0.40), claim.amount(new Date(), 1_000_000.0, 0.40), TOL);
    }
}
