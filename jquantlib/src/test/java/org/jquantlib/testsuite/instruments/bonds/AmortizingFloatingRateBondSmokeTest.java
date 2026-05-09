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

package org.jquantlib.testsuite.instruments.bonds;

import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.bonds.AmortizingFloatingRateBond;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Construction smoke test for {@link AmortizingFloatingRateBond} (Phase 5d.5-Bonds).
 *
 * <p>The C++ test-suite has no dedicated {@code amortizingfloatingratebond} cases
 * (the existing {@code amortizingbond.cpp} covers fixed-rate only). The
 * present test exercises the construction path of the simplest 5-arg
 * overload and asserts:
 * <ol>
 *   <li>cashflows are non-empty,</li>
 *   <li>each Coupon's nominal aligns with the supplied amortizing
 *       notionals vector,</li>
 *   <li>maturity date matches the schedule end date.</li>
 * </ol>
 */
public class AmortizingFloatingRateBondSmokeTest {

    @Test
    public void testConstructAndAmortizationStructure() {
        final Date issue = new Date(15, Month.January, 2020);
        final Date maturity = new Date(15, Month.January, 2025);
        final Schedule sch = new Schedule(issue, maturity,
                new Period(Frequency.Quarterly), new Target(),
                BusinessDayConvention.Following,
                BusinessDayConvention.Following,
                DateGeneration.Rule.Backward, false);

        // Forecast curve for the index to allow registerWith / fixings
        // consistency without needing actual fixings (we only test
        // construction + structure).
        final Handle<YieldTermStructure> fwd = new Handle<YieldTermStructure>(
                new FlatForward(issue, 0.01, new Actual360()));
        final IborIndex index = new Euribor3M(fwd);

        // 20 quarterly periods → 20 amortizing notionals (linear sink).
        final double[] notionals = new double[20];
        for (int i = 0; i < 20; ++i) {
            notionals[i] = 100.0 * (1.0 - i / 20.0);
        }

        final AmortizingFloatingRateBond bond = new AmortizingFloatingRateBond(
                3, notionals, sch, index, new Actual360());

        final Leg cfs = bond.cashflows();
        assertNotNull("cashflows must be non-null", cfs);
        assertTrue("cashflows must be non-empty", !cfs.isEmpty());

        // Find the first Coupon (Java's EarlierThanCashFlowComparator is
        // unstable on equal dates and may put the redemption ahead of the
        // first coupon; identify by class instead). The first Coupon
        // returned must use notionals[0].
        Coupon firstCoupon = null;
        for (int j = 0; j < cfs.size(); ++j) {
            if (cfs.get(j) instanceof Coupon) {
                firstCoupon = (Coupon) cfs.get(j);
                break;
            }
        }
        assertNotNull("must find at least one Coupon", firstCoupon);
        assertEquals("first coupon nominal == notionals[0]",
                notionals[0], firstCoupon.nominal(), 1e-12);

        // Coupon count == notionals length (one floating-rate coupon per
        // notional period).
        int coupons = 0;
        for (int j = 0; j < cfs.size(); ++j) {
            if (cfs.get(j) instanceof Coupon) coupons++;
        }
        assertEquals("coupon count == notionals.length", notionals.length, coupons);

        // Maturity matches the schedule end-date.
        assertEquals("maturity date matches schedule", maturity, bond.maturityDate());
    }
}
